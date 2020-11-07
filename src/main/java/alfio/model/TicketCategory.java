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

import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;

@Getter
public class TicketCategory {

    public static final Comparator<TicketCategory> COMPARATOR = (tc1, tc2) -> new CompareToBuilder().append(tc1.utcInception, tc2.utcInception).append(tc1.utcExpiration, tc2.utcExpiration).toComparison();

    public enum Status {
        ACTIVE, NOT_ACTIVE
    }

    /**
     * Defines the check-in strategy that must be adopted for this TicketCategory
     */
    public enum TicketCheckInStrategy {
        /**
         * Default, retro-compatible. Attendees can check-in only once, using their ticket
         */
        ONCE_PER_EVENT,
        /**
         * Extends ONCE_PER_EVENT by adding the possibility to re-enter the event by using the printed badge.
         * If an attendee tries to enter more than once in a single day, the operator will receive a warning.
         * ** IMPORTANT **:
         * additional check-ins can be done only using the printed badge.
         * This ensures that a badge is printed only once
         */
        ONCE_PER_DAY
    }

    /**
     * Defines the category access type. It will impact how tickets are generated/sent/checked-in.
     */
    public enum TicketAccessType {
        /**
         * Inherit the access type from the Event format (default)
         */
        INHERIT,

        /**
         * Attendees of this category will receive a ticket and Wallet Pass, and will be allowed to access
         * the event venue.
         */
        IN_PERSON,

        /**
         * Attendees of this category can only access the event online.
         */
        ONLINE
    }

    private final int id;
    private final ZonedDateTime utcInception;
    private final ZonedDateTime utcExpiration;
    private final int maxTickets;
    private final String name;
    private final boolean accessRestricted;
    private final Status status;
    private final int eventId;
    private final boolean bounded;
    private final int srcPriceCts;
    private final String code;
    private final ZonedDateTime validCheckInFrom;
    private final ZonedDateTime validCheckInTo;
    private final ZonedDateTime ticketValidityStart;
    private final ZonedDateTime ticketValidityEnd;
    private final String currencyCode;
    private final int ordinal;
    private final TicketCheckInStrategy ticketCheckInStrategy;
    private final TicketAccessType ticketAccessType;


    public TicketCategory(@JsonProperty("id") @Column("id") int id,
                          @JsonProperty("utcInception") @Column("inception") ZonedDateTime utcInception,
                          @JsonProperty("utcExpiration") @Column("expiration") ZonedDateTime utcExpiration,
                          @JsonProperty("maxTickets") @Column("max_tickets") int maxTickets,
                          @JsonProperty("name") @Column("name") String name,
                          @JsonProperty("accessRestricted") @Column("access_restricted") boolean accessRestricted,
                          @JsonProperty("status") @Column("tc_status") Status status,
                          @JsonProperty("eventId") @Column("event_id") int eventId,
                          @JsonProperty("bounded") @Column("bounded") boolean bounded,
                          @JsonProperty("srcPriceCts") @Column("src_price_cts") int srcPriceCts,
                          @JsonProperty("code") @Column("category_code") String code,
                          @JsonProperty("validCheckInFrom") @Column("valid_checkin_from") ZonedDateTime validCheckInFrom,
                          @JsonProperty("validCheckInTo") @Column("valid_checkin_to") ZonedDateTime validCheckInTo,
                          @JsonProperty("ticketValidityStart") @Column("ticket_validity_start") ZonedDateTime ticketValidityStart,
                          @JsonProperty("ticketValidityEnd") @Column("ticket_validity_end") ZonedDateTime ticketValidityEnd,
                          @JsonProperty("currencyCode") @Column("currency_code") String currencyCode,
                          @JsonProperty("ordinal") @Column("ordinal") Integer ordinal,
                          @JsonProperty("ticketCheckInStrategy") @Column("ticket_checkin_strategy") TicketCheckInStrategy ticketCheckInStrategy,
                          @JsonProperty("ticketAccessType") @Column("ticket_access_type") TicketAccessType ticketAccessType) {
        this.id = id;
        this.utcInception = utcInception;
        this.utcExpiration = utcExpiration;
        this.maxTickets = maxTickets;
        this.name = name;
        this.accessRestricted = accessRestricted;
        this.status = status;
        this.eventId = eventId;
        this.bounded = bounded;
        this.srcPriceCts = srcPriceCts;
        this.code = code;
        this.validCheckInFrom = validCheckInFrom;
        this.validCheckInTo = validCheckInTo;
        this.ticketValidityStart = ticketValidityStart;
        this.ticketValidityEnd = ticketValidityEnd;
        this.currencyCode = currencyCode;
        this.ordinal = ordinal != null ? ordinal : 0;
        this.ticketCheckInStrategy = ticketCheckInStrategy;
        this.ticketAccessType = ticketAccessType;
    }

    public BigDecimal getPrice() {
        return MonetaryUtil.centsToUnit(srcPriceCts, currencyCode);
    }
    
    public boolean getFree() {
        return srcPriceCts == 0;
    }

    public ZonedDateTime getInception(ZoneId zoneId) {
        return utcInception.withZoneSameInstant(zoneId);
    }

    public ZonedDateTime getExpiration(ZoneId zoneId) {
        return utcExpiration.withZoneSameInstant(zoneId);
    }

    public ZonedDateTime getValidCheckInFrom(ZoneId zoneId) {
        return validCheckInFrom == null ? null : validCheckInFrom.withZoneSameInstant(zoneId);
    }

    public ZonedDateTime getValidCheckInTo(ZoneId zoneId) {
        return validCheckInTo == null ? null : validCheckInTo.withZoneSameInstant(zoneId);
    }

    public ZonedDateTime getTicketValidityStart(ZoneId zoneId) {
        return ticketValidityStart == null ? null : ticketValidityStart.withZoneSameInstant(zoneId);
    }

    public ZonedDateTime getTicketValidityEnd(ZoneId zoneId) {
        return ticketValidityEnd == null ? null : ticketValidityEnd.withZoneSameInstant(zoneId);
    }

    public boolean hasValidCheckIn(ZonedDateTime now, ZoneId eventZoneId) {
        // check from boundary -> from cannot be after now
        // check to boundary -> to cannot be before now
        return (validCheckInFrom == null || !getValidCheckInFrom(eventZoneId).isAfter(now)) && (validCheckInTo == null || !getValidCheckInTo(eventZoneId).isBefore(now));
    }
}
