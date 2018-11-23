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

import java.time.ZonedDateTime;
import java.util.Map;

@Getter
public class BillingDocument {

    public enum Type {
        INVOICE, RECEIPT
    }

    public enum Status {
        VALID, NOT_VALID
    }

    private final int id;
    private final int eventId;
    private final String number;
    private final String reservationId;
    private final Type type;
    private final Map<String, Object> model;
    private final ZonedDateTime generationTimestamp;
    private final Status status;

    public BillingDocument(@Column("id") int id,
                           @Column("event_id_fk") int eventId,
                           @Column("reservation_id_fk") String reservationId,
                           @Column("number") String number,
                           @Column("type") Type type,
                           @Column("model") String model,
                           @Column("generation_ts") ZonedDateTime generationTimestamp,
                           @Column("status") Status status) {
        this.id = id;
        this.eventId = eventId;
        this.number = number;
        this.reservationId = reservationId;
        this.type = type;
        this.model = Json.fromJson(model, new TypeReference<Map<String, Object>>() {});
        this.generationTimestamp = generationTimestamp;
        this.status = status;
    }
}
