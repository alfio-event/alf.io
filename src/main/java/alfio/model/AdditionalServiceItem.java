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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class AdditionalServiceItem {

    // are considered "Confirmed": ACQUIRED, CHECKED_IN and TO_BE_PAID
    public enum AdditionalServiceItemStatus {
        FREE,
        PENDING, TO_BE_PAID, ACQUIRED, CANCELLED,
        CHECKED_IN,
        EXPIRED,
        INVALIDATED, RELEASED
    }

    private final int id;
    private final String uuid;
    private final ZonedDateTime utcCreation;
    private final ZonedDateTime utcLastModified;
    private final String ticketsReservationUuid;
    private final int additionalServiceId;
    private final AdditionalServiceItemStatus status;
    private final int eventId;
    private final Integer ticketId;

    private final Integer srcPriceCts;
    private final Integer finalPriceCts;
    private final Integer vatCts;
    private final Integer discountCts;
    private final String  currencyCode;

    public AdditionalServiceItem(@Column("id") int id,
                                 @Column("uuid") String uuid,
                                 @Column("creation") ZonedDateTime utcCreation,
                                 @Column("last_modified") ZonedDateTime utcLastModified,
                                 @Column("tickets_reservation_uuid") String ticketsReservationUuid,
                                 @Column("additional_service_id_fk") int additionalServiceId,
                                 @Column("status") AdditionalServiceItemStatus status,
                                 @Column("event_id_fk") int eventId,
                                 @Column("src_price_cts") Integer srcPriceCts,
                                 @Column("final_price_cts") Integer finalPriceCts,
                                 @Column("vat_cts") Integer vatCts,
                                 @Column("discount_cts") Integer discountCts,
                                 @Column("currency_code") String currencyCode,
                                 @Column("ticket_id_fk") Integer ticketId) {
        this.id = id;
        this.uuid = uuid;
        this.utcCreation = utcCreation;
        this.utcLastModified = utcLastModified;
        this.ticketsReservationUuid = ticketsReservationUuid;
        this.additionalServiceId = additionalServiceId;
        this.status = status;
        this.eventId = eventId;
        this.srcPriceCts = srcPriceCts;
        this.finalPriceCts = finalPriceCts;
        this.vatCts = vatCts;
        this.discountCts = discountCts;
        this.currencyCode = currencyCode;
        this.ticketId = ticketId;
    }
}
