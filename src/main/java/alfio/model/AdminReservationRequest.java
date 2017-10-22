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
package alfio.model;

import alfio.model.modification.AdminReservationModification;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class AdminReservationRequest {

    public enum RequestType {
        ADMIN, IMPORT
    }

    public enum Status {
        PENDING, SUCCESS, ERROR
    }

    private final long id;
    private final String requestId;
    private final long userId;
    private final long eventId;
    private final String reservationId;
    private final RequestType requestType;
    private final Status status;
    private final AdminReservationModification body;
    private final String failureCode;

    public AdminReservationRequest(@Column("id") long id,
                                   @Column("request_id") String requestId,
                                   @Column("user_id") long userId,
                                   @Column("event_id") long eventId,
                                   @Column("reservation_id") String reservationId,
                                   @Column("request_type") RequestType requestType,
                                   @Column("status") Status status,
                                   @Column("body") String body,
                                   @Column("failure_code") String failureCode) {
        this.id = id;
        this.requestId = requestId;
        this.userId = userId;
        this.eventId = eventId;
        this.reservationId = reservationId;
        this.requestType = requestType;
        this.status = status;
        this.body = Json.fromJson(body, AdminReservationModification.class);
        this.failureCode = failureCode;
    }

}
