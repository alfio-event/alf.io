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
import lombok.Getter;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;

@Getter
public class TicketCategory {

    public static Comparator<TicketCategory> COMPARATOR = (tc1, tc2) -> new CompareToBuilder().append(tc1.utcInception, tc2.utcInception).append(tc1.utcExpiration, tc2.utcExpiration).toComparison();

    public enum Status {
        ACTIVE, NOT_ACTIVE
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

    public TicketCategory(@Column("id") int id,
                          @Column("inception") ZonedDateTime utcInception,
                          @Column("expiration") ZonedDateTime utcExpiration,
                          @Column("max_tickets") int maxTickets,
                          @Column("name") String name,
                          @Column("access_restricted") boolean accessRestricted,
                          @Column("tc_status") Status status,
                          @Column("event_id") int eventId,
                          @Column("bounded") boolean bounded,
                          @Column("src_price_cts") int srcPriceCts) {
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
    }

    public BigDecimal getPrice() {
        //TODO: apply this conversion only for some currency. Not all are cent based.
        return MonetaryUtil.centsToUnit(srcPriceCts);
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
}
