/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.manager;

import alfio.model.*;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.CustomerData;
import alfio.model.modification.AdminReservationModification.TransactionDetails;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.user.User;
import alfio.repository.AdminReservationRequestRepository;
import alfio.repository.EventRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ClockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.modification.AdminReservationModification.Notification.orEmpty;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

@Component
@Transactional
@Log4j2
@RequiredArgsConstructor
public class AdminReservationRequestManager {

    private final AdminReservationManager adminReservationManager;
    private final EventManager eventManager;
    private final UserRepository userRepository;
    private final AdminReservationRequestRepository adminReservationRequestRepository;
    private final EventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;
    private final ClockProvider clockProvider;

    public Result<AdminReservationRequestStats> getRequestStatus(String requestId, String eventName, String username) {
        return eventManager.getOptionalEventAndOrganizationIdByName(eventName, username)
            .flatMap(e -> adminReservationRequestRepository.findStatsByRequestIdAndEventId(requestId, e.getId()))
            .map(Result::success)
            .orElseGet(() -> Result.error(ErrorCode.EventError.ACCESS_DENIED));
    }

    public Result<String> scheduleReservations(String eventName,
                                               AdminReservationModification body,
                                               boolean singleReservation,
                                               String username) {

        //safety check: if there are more than 150 people in a single reservation, the reservation page could take a while before showing up,
        //therefore we will limit the maximum amount of people in a single reservation to 100. This will be addressed in rel. 2.0

        if(singleReservation && body.getTicketsInfo().stream().mapToLong(ti -> ti.getAttendees().size()).sum() > 100) {
            return Result.error(ErrorCode.custom("MAX_NUMBER_EXCEEDED", "Maximum allowed attendees per reservation is 100"));
        }

        // #620 - validate reference:
        var attendeesWithDuplicateReference = body.getTicketsInfo().stream().flatMap(ti -> ti.getAttendees().stream())
            .filter(attendee -> StringUtils.isNotEmpty(attendee.getReference()))
            .collect(Collectors.groupingBy(attendee -> attendee.getReference().trim(), Collectors.counting()))
            .entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .limit(5) // return max 5 codes
            .collect(Collectors.toList());

        if(!attendeesWithDuplicateReference.isEmpty()) {
            return Result.error(ErrorCode.custom("DUPLICATE_REFERENCE", "The following codes are duplicate:" + attendeesWithDuplicateReference));
        }


        return eventManager.getOptionalByName(eventName, username)
            .map(event -> adminReservationManager.validateTickets(body, event))
            .map(request -> request.flatMap(pair -> insertRequest(pair.getRight(), pair.getLeft(), singleReservation, username)))
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED));
    }

    //TODO: rewrite, and add test!
    public Pair<Integer, Integer> processPendingReservations() {
        Map<Boolean, List<MapSqlParameterSource>> result = adminReservationRequestRepository.findPendingForUpdate(1000)
            .stream()
            .map(id -> {
                AdminReservationRequest request = adminReservationRequestRepository.fetchCompleteById(id);

                Result<Triple<TicketReservation, List<Ticket>, Event>> reservationResult = Result.fromNullable(eventRepository.findOptionalById((int) request.getEventId()).orElse(null), ErrorCode.EventError.NOT_FOUND)
                    .flatMap(e -> Result.fromNullable(userRepository.findOptionalById((int) request.getUserId()).map(u -> Pair.of(e, u)).orElse(null), ErrorCode.EventError.ACCESS_DENIED))
                    .flatMap(p -> processReservation(request, p.getLeft(), p.getRight()));
                return buildParameterSource(id, reservationResult);
            }).collect(Collectors.partitioningBy(ps -> AdminReservationRequest.Status.SUCCESS.name().equals(ps.getValue("status"))));

        result.values().forEach(list -> {
            try {
                adminReservationRequestRepository.updateStatus(list);
            } catch(Exception e) {
                log.warn("cannot update the status of "+list.size()+" reservations", e);
            }
        });

        return Pair.of(CollectionUtils.size(result.get(true)), CollectionUtils.size(result.get(false)));

    }

    private Result<Triple<TicketReservation, List<Ticket>, Event>> processReservation(AdminReservationRequest request, Event event, User user) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            var savepoint = status.createSavepoint();
            try {
                String eventName = event.getShortName();
                String username = user.getUsername();
                var requestBody = request.getBody();
                Result<Triple<TicketReservation, List<Ticket>, Event>> result = adminReservationManager.createReservation(requestBody, eventName, username)
                    .flatMap(r -> adminReservationManager.confirmReservation(PurchaseContext.PurchaseContextType.event,
                        eventName,
                        r.getLeft().getId(),
                        username,
                        orEmpty(requestBody.getNotification()),
                        TransactionDetails.admin(),
                        requestBody.getLinkedSubscriptionId()))
                    .map(triple -> Triple.of(triple.getLeft(), triple.getMiddle(), (Event) triple.getRight()));
                if(!result.isSuccess()) {
                    status.rollbackToSavepoint(savepoint);
                    log.warn("Cannot process reservation: \n{}", result.getFormattedErrors());
                }
                return result;
            } catch(Exception ex) {
                status.rollbackToSavepoint(savepoint);
                return Result.error(singletonList(ErrorCode.custom("", ex.getMessage())));
            }
        });
    }

    private MapSqlParameterSource buildParameterSource(Long id, Result<Triple<TicketReservation, List<Ticket>, Event>> result) {
        boolean success = result.isSuccess();
        if (!success) {
            log.warn("Cannot process request {}. Got the following errors:\n{}", id, result.getFormattedErrors());
        }
        return new MapSqlParameterSource("id", id)
            .addValue("status", success ? AdminReservationRequest.Status.SUCCESS.name() : AdminReservationRequest.Status.ERROR.name())
            .addValue("reservationId", success ? result.getData().getLeft().getId() : null)
            .addValue("failureCode", success ? null : ofNullable(result.getFirstErrorOrNull()).map(ErrorCode::getCode).orElse(null));
    }

    private Result<String> insertRequest(AdminReservationModification body, EventAndOrganizationId event, boolean singleReservation, String username) {
        try {
            return insertRequest(UUID.randomUUID().toString(), body, event, singleReservation, username);
        } catch(DataIntegrityViolationException e) {
            log.error("data integrity violation while inserting request", e);
            return Result.error(ErrorCode.custom("internal_server_error", e.getMessage()));
        }
    }

    /**
     * Insert a request using the provided ID and body
     *
     * @param requestId requestId, must be unique
     * @param body request body which can be split across multiple reservations
     * @param event the event for which we need to create reservations
     * @param singleReservation whether the request will produce one or more reservations
     * @param username user requesting the import
     * @return {@code Result} the operation result
     * @throws DataIntegrityViolationException if the given ID already exists
     */
    public Result<String> insertRequest(String requestId,
                                 AdminReservationModification body,
                                 EventAndOrganizationId event,
                                 boolean singleReservation,
                                 String username) {
        try {
            long userId = userRepository.findIdByUserName(username).orElseThrow(IllegalArgumentException::new);
            adminReservationRequestRepository.insertRequest(requestId, userId, event, spread(body, singleReservation));
            return Result.success(requestId);
        } catch (DataIntegrityViolationException e) {
            throw e;
        } catch (Exception e) {
            log.error("can't insert reservation request", e);
            return Result.error(ErrorCode.custom("internal_server_error", e.getMessage()));
        }
    }

    private Stream<AdminReservationModification> spread(AdminReservationModification src, boolean single) {
        if(single) {
            return Stream.of(src);
        }
        return src.getTicketsInfo()
            .stream()
            .flatMap(ti -> ti.getAttendees().stream().map(a -> Pair.of(a, new AdminReservationModification.TicketsInfo(ti.getCategory(), singletonList(a), ti.isAddSeatsIfNotAvailable(), ti.isUpdateAttendees()))))
            .map(p -> {
                AdminReservationModification.Attendee attendee = p.getLeft();
                String language = StringUtils.defaultIfBlank(attendee.getLanguage(), src.getLanguage());

                CustomerData cd = new CustomerData(attendee.getFirstName(), attendee.getLastName(), attendee.getEmailAddress(), null, language, null, null, null, null);
                return new AdminReservationModification(src.getExpiration(),
                    cd,
                    singletonList(p.getRight()),
                    language,
                    src.isUpdateContactData(),
                    false,
                    null,
                    src.getNotification(),
                    null,
                    attendee.getSubscriptionId());
            });
    }

}
