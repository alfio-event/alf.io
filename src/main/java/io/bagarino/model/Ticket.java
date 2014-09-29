/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
public class Ticket {

    public enum TicketStatus {
        FREE, PENDING, ACQUIRED, CANCELLED, CHECKED_IN, EXPIRED
    }

    private final int id;
    private final String uuid;
    private final Date creation;
    private final int categoryId;
    private final int eventId;
    private final TicketStatus status;
    private final BigDecimal originalPrice;
    private final BigDecimal paidPrice;
    private final String ticketsReservationId;
    
    public Ticket(@Column("id") int id,
                  @Column("uuid") String uuid,
                  @Column("creation") Date creation,
                  @Column("category_id") int categoryId,
                  @Column("status") String status,
                  @Column("event_id") int eventId,
                  @Column("original_price") BigDecimal originalPrice,
                  @Column("paid_price") BigDecimal paidPrice,
                  @Column("tickets_reservation_id") String ticketsReservationId) {
        this.id = id;
        this.uuid = uuid;
        this.creation = creation;
        this.categoryId = categoryId;
        this.eventId = eventId;
        this.status = TicketStatus.valueOf(status);
        this.originalPrice = originalPrice;
        this.paidPrice = paidPrice;
        this.ticketsReservationId = ticketsReservationId;
    }
}
