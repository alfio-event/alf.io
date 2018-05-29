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
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.user.User;
import alfio.repository.AdminReservationRequestRepository;
import alfio.repository.EventRepository;
import alfio.repository.user.UserRepository;
import alfio.util.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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

import static alfio.util.OptionalWrapper.optionally;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

@Component
@Transactional
@Log4j2
@RequiredArgsConstructor
public class AdminReservationRequestManager {

    private final AdminReservationManager adminReservationManager;
    private final EventManager eventManager;
    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final AdminReservationRequestRepository adminReservationRequestRepository;
    private final EventRepository eventRepository;
    private final PlatformTransactionManager transactionManager;

    public Result<AdminReservationRequestStats> getRequestStatus(String requestId, String eventName, String username) {
        return eventManager.getOptionalByName(eventName, username)
            .flatMap(e -> adminReservationRequestRepository.findStatsByRequestIdAndEventId(requestId, e.getId()))
            .map(Result::success)
            .orElseGet(() -> Result.error(ErrorCode.EventError.ACCESS_DENIED));
    }

    public Result<String> scheduleReservations(String eventName,
                                               AdminReservationModification body,
                                               boolean singleReservation,
                                               String username) {

        //safety check: if there are more than 150 people in a single reservation, the reservation page could take a while before showing up.
        //therefore we will limit the maximum amount of people in a single reservation to 100. This will be addressed in rel. 2.0

        if(singleReservation && body.getTicketsInfo().stream().mapToLong(ti -> ti.getAttendees().size()).sum() > 100) {
            return Result.error(ErrorCode.custom("MAX_NUMBER_EXCEEDED", "Maximum allowed attendees per reservation is 100"));
        }

        return eventManager.getOptionalByName(eventName, username)
            .map(event -> adminReservationManager.validateTickets(body, event))
            .map(request -> request.flatMap(pair -> insertRequest(pair.getRight(), pair.getLeft(), singleReservation, username)))
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED));
    }

    Pair<Integer, Integer> processPendingReservations() {
        Map<Boolean, List<MapSqlParameterSource>> result = adminReservationRequestRepository.findPendingForUpdate(1000)
            .stream()
            .map(id -> {
                AdminReservationRequest request = adminReservationRequestRepository.fetchCompleteById(id);

                Result<Triple<TicketReservation, List<Ticket>, Event>> reservationResult = Result.fromNullable(optionally(() -> eventRepository.findById((int) request.getEventId())).orElse(null), ErrorCode.EventError.NOT_FOUND)
                    .flatMap(e -> Result.fromNullable(optionally(() -> userRepository.findById((int) request.getUserId())).map(u -> Pair.of(e, u)).orElse(null), ErrorCode.EventError.ACCESS_DENIED))
                    .flatMap(p -> processReservation(request, p));
                return buildParameterSource(id, reservationResult);
            }).collect(Collectors.partitioningBy(ps -> AdminReservationRequest.Status.SUCCESS.name().equals(ps.getValue("status"))));

        result.values().forEach(list -> {
            try {
                jdbc.batchUpdate(adminReservationRequestRepository.updateStatus(), list.toArray(new MapSqlParameterSource[list.size()]));
            } catch(Exception e) {
                log.fatal("cannot update the status of "+list.size()+" reservations", e);
            }
        });

        return Pair.of(CollectionUtils.size(result.get(true)), CollectionUtils.size(result.get(false)));

    }

    private Result<Triple<TicketReservation, List<Ticket>, Event>> processReservation(AdminReservationRequest request, Pair<Event, User> p) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionTemplate template = new TransactionTemplate(transactionManager, definition);
        return template.execute(status -> {
            try {
                String eventName = p.getLeft().getShortName();
                String username = p.getRight().getUsername();
                Result<Triple<TicketReservation, List<Ticket>, Event>> result = adminReservationManager.createReservation(request.getBody(), eventName, username)
                    .flatMap(r -> adminReservationManager.confirmReservation(eventName, r.getLeft().getId(), username));
                if(!result.isSuccess()) {
                    status.setRollbackOnly();
                }
                return result;
            } catch(Exception ex) {
                status.setRollbackOnly();
                return Result.error(singletonList(ErrorCode.custom("", ex.getMessage())));
            }
        });
    }

    private MapSqlParameterSource buildParameterSource(Long id, Result<Triple<TicketReservation, List<Ticket>, Event>> result) {
        boolean success = result.isSuccess();
        return new MapSqlParameterSource("id", id)
            .addValue("status", success ? AdminReservationRequest.Status.SUCCESS.name() : AdminReservationRequest.Status.ERROR.name())
            .addValue("reservationId", success ? result.getData().getLeft().getId() : null)
            .addValue("failureCode", success ? null : ofNullable(result.getFirstErrorOrNull()).map(ErrorCode::getCode).orElse(null));
    }

    private Result<String> insertRequest(AdminReservationModification body, Event event, boolean singleReservation, String username) {
        try {
            String requestId = UUID.randomUUID().toString();
            long userId = userRepository.findIdByUserName(username).orElseThrow(IllegalArgumentException::new);
            MapSqlParameterSource[] requests = spread(body, singleReservation)
                .map(res -> new MapSqlParameterSource("userId", userId)
                    .addValue("requestId", requestId)
                    .addValue("requestType", AdminReservationRequest.RequestType.IMPORT.name())
                    .addValue("status", AdminReservationRequest.Status.PENDING.name())
                    .addValue("eventId", event.getId())
                    .addValue("body", Json.toJson(res)))
                .toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate(adminReservationRequestRepository.insertRequest(), requests);
            return Result.success(requestId);
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
                CustomerData cd = new CustomerData(attendee.getFirstName(), attendee.getLastName(), attendee.getEmailAddress(), null, language, null);
                return new AdminReservationModification(src.getExpiration(), cd, singletonList(p.getRight()), language, src.isUpdateContactData(), src.getNotification());
            });
    }

}
