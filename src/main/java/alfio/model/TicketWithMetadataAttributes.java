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

import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.support.Array;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.ZonedDateTime;
import java.util.*;

public class TicketWithMetadataAttributes {

    private final Ticket ticket;
    private final TicketMetadataContainer ticketMetadataContainer;

    public TicketWithMetadataAttributes(@Column("id") int id,
                                        @Column("uuid") String uuid,
                                        @Column("creation") ZonedDateTime creation,
                                        @Column("category_id") Integer categoryId,
                                        @Column("status") String status,
                                        @Column("event_id") int eventId,
                                        @Column("tickets_reservation_id") String ticketsReservationId,
                                        @Column("full_name") String fullName,
                                        @Column("first_name") String firstName,
                                        @Column("last_name") String lastName,
                                        @Column("email_address") String email,
                                        @Column("locked_assignment") boolean lockedAssignment,
                                        @Column("user_language") String userLanguage,
                                        @Column("src_price_cts") int srcPriceCts,
                                        @Column("final_price_cts") int finalPriceCts,
                                        @Column("vat_cts") int vatCts,
                                        @Column("discount_cts") int discountCts,
                                        @Column("ext_reference") String extReference,
                                        @Column("currency_code") String currencyCode,
                                        @Column("tags") @Array List<String> tags,
                                        @Column("subscription_id_fk") UUID subscriptionId,
                                        @Column("vat_status") PriceContainer.VatStatus vatStatus,
                                        @Column("metadata") @JSONData TicketMetadataContainer ticketMetadataContainer) {
        this(new Ticket(id, uuid, creation, categoryId, status, eventId, ticketsReservationId, fullName, firstName, lastName, email, lockedAssignment, userLanguage, srcPriceCts, finalPriceCts, vatCts, discountCts, extReference, currencyCode, tags, subscriptionId, vatStatus),
            ticketMetadataContainer);
    }

    private TicketWithMetadataAttributes(Ticket ticket, TicketMetadataContainer ticketMetadataContainer) {
        this.ticket = ticket;
        this.ticketMetadataContainer = Objects.requireNonNullElseGet(ticketMetadataContainer, TicketMetadataContainer::empty);
    }

    public Map<String, String> getAttributes() {
        return ticketMetadataContainer
            .getMetadataForKey(TicketMetadataContainer.GENERAL)
            .flatMap(tm -> Optional.ofNullable(tm.getAttributes()))
            .orElse(Map.of());
    }

    public Ticket getTicket() {
        return ticket;
    }

    @JsonIgnore
    public TicketMetadataContainer getMetadata() {
        return ticketMetadataContainer;
    }

    public static TicketWithMetadataAttributes build(Ticket ticket, TicketMetadataContainer ticketMetadataContainer) {
        return new TicketWithMetadataAttributes(ticket, ticketMetadataContainer);
    }
}
