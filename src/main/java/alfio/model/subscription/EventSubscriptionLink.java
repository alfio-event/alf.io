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
package alfio.model.subscription;

import alfio.model.support.JSONData;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Getter
public class EventSubscriptionLink {
    private final UUID subscriptionDescriptorId;
    private final Map<String, String> subscriptionDescriptorTitle;
    private final int eventId;
    private final String eventShortName;
    private final String eventDisplayName;
    private final int pricePerTicket;
    private final String eventCurrency;


    public EventSubscriptionLink(@Column("subscription_descriptor_id") UUID subscriptionDescriptorId,
                                 @Column("subscription_descriptor_title") @JSONData Map<String, String> subscriptionDescriptorTitle,
                                 @Column("event_id") int eventId,
                                 @Column("event_short_name") String eventShortName,
                                 @Column("event_display_name") String eventDisplayName,
                                 @Column("event_currency") String eventCurrency,
                                 @Column("price_per_ticket") int pricePerTicket) {
        this.subscriptionDescriptorId = subscriptionDescriptorId;
        this.subscriptionDescriptorTitle = subscriptionDescriptorTitle;
        this.eventId = eventId;
        this.eventShortName = eventShortName;
        this.eventCurrency = eventCurrency;
        this.eventDisplayName = eventDisplayName;
        this.pricePerTicket = pricePerTicket;
    }

    public BigDecimal getFormattedPricePerTicket() {
        return MonetaryUtil.centsToUnit(pricePerTicket, eventCurrency);
    }
}
