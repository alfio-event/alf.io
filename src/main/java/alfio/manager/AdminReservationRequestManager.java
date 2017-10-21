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

import alfio.model.AdminReservationRequest;
import alfio.model.Event;
import alfio.model.modification.AdminReservationModification;
import alfio.model.modification.AdminReservationModification.CustomerData;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.AdminReservationRequestRepository;
import alfio.repository.user.UserRepository;
import alfio.util.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;

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

    public Result<List<AdminReservationRequest>> getRequestStatus(String requestId, String eventName, String username) {
        return eventManager.getOptionalByName(eventName, username)
            .map(event -> Result.success(adminReservationRequestRepository.findByRequestIdAndEventId(requestId, event.getId())))
            .orElseGet(() -> Result.error(ErrorCode.EventError.ACCESS_DENIED));
    }

    public Result<String> scheduleReservations(String eventName,
                                               AdminReservationModification body,
                                               boolean singleReservation,
                                               String username) {

        return eventManager.getOptionalByName(eventName, username)
            .map(event -> adminReservationManager.validateTickets(body, event))
            .map(request -> request.flatMap(pair -> insertRequest(pair.getRight(), pair.getLeft(), singleReservation, username)))
            .orElseGet(() -> Result.error(ErrorCode.ReservationError.UPDATE_FAILED));
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
                CustomerData cd = new CustomerData(attendee.getFirstName(), attendee.getLastName(), attendee.getEmailAddress(), null, language);
                return new AdminReservationModification(src.getExpiration(), cd, singletonList(p.getRight()), language, src.isUpdateContactData(), src.getNotification());
            });
    }

}
