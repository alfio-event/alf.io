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

import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
public class Audit {

    public enum EntityType {
        EVENT, TICKET, RESERVATION
    }

    public enum EventType {
        RESERVATION_CREATE,
        RESERVATION_COMPLETE,
        RESERVATION_OFFLINE_PAYMENT_CONFIRMED,
        CANCEL_RESERVATION_EXPIRED,
        CANCEL_RESERVATION,
        UPDATE_EVENT,
        CANCEL_TICKET,
        REFUND,
        CHECK_IN,
        MANUAL_CHECK_IN,
        REVERT_CHECK_IN,
        UPDATE_TICKET,
        UPDATE_TICKET_CATEGORY,
        UPDATE_INVOICE,
        FORCED_UPDATE_INVOICE,
        TERMS_CONDITION_ACCEPTED,
        PRIVACY_POLICY_ACCEPTED
    }

    private final String reservationId;
    private final EventType eventType;
    private final Date eventTime;
    private final EntityType entityType;
    private final String entityId;
    private final List<Map<String, Object>> modifications;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;


    public Audit(@Column("reservation_id") String reservationId, @Column("event_type") EventType eventType,
                 @Column("event_time") Date eventTime, @Column("entity_type") EntityType entityType,
                 @Column("entity_id") String entityId, @Column("modifications") String modifications,
                 @Column("username") String username, @Column("first_name") String firstName,
                 @Column("last_name") String lastName, @Column("email_address") String email) {
        this.reservationId = reservationId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.entityType = entityType;
        this.entityId = entityId;
        this.modifications = modifications == null ? null : Json.fromJson(modifications, new TypeReference<List<Map<String, Object>>>() {});
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }
}
