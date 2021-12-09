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

import alfio.model.PriceContainer;
import alfio.model.support.Array;
import alfio.model.support.JSONData;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class SubscriptionDescriptorWithStatistics {

    private final int soldCount;
    private final int pendingCount;
    private final int linkedEventsCount;
    private final int reservationsCount;
    private final SubscriptionDescriptor descriptor;

    public SubscriptionDescriptorWithStatistics(

        @Column("sd_id") UUID id,
        @Column("sd_title") @JSONData Map<String, String> title,
        @Column("sd_description") @JSONData Map<String, String> description,
        @Column("sd_max_available") int maxAvailable,
        @Column("sd_creation_ts") ZonedDateTime creation,
        @Column("sd_on_sale_from") ZonedDateTime onSaleFrom,
        @Column("sd_on_sale_to") ZonedDateTime onSaleTo,
        @Column("sd_price_cts") int price,
        @Column("sd_vat") BigDecimal vat,
        @Column("sd_vat_status") PriceContainer.VatStatus vatStatus,
        @Column("sd_currency") String currency,
        @Column("sd_is_public") boolean isPublic,
        @Column("sd_organization_id_fk") int organizationId,

        @Column("sd_max_entries") int maxEntries,
        @Column("sd_validity_type") SubscriptionDescriptor.SubscriptionValidityType validityType,
        @Column("sd_validity_time_unit") SubscriptionDescriptor.SubscriptionTimeUnit validityTimeUnit,
        @Column("sd_validity_units") Integer validityUnits,
        @Column("sd_validity_from") ZonedDateTime validityFrom,
        @Column("sd_validity_to") ZonedDateTime validityTo,
        @Column("sd_usage_type") SubscriptionDescriptor.SubscriptionUsageType usageType,

        @Column("sd_terms_conditions_url") String termsAndConditionsUrl,
        @Column("sd_privacy_policy_url") String privacyPolicyUrl,
        @Column("sd_file_blob_id_fk") String fileBlobId,
        @Column("sd_allowed_payment_proxies") @Array List<String> paymentProxies,
        @Column("sd_private_key") String privateKey,
        @Column("sd_time_zone") String timeZone,
        @Column("sd_supports_tickets_generation") Boolean supportsTicketsGeneration,


        @Column("s_pending_count") int pendingCount,
        @Column("s_sold_count") int soldCount,
        @Column("s_reservations_count") int reservationsCount,
        @Column("s_events_count") int linkedEventsCount) {

        this.pendingCount = pendingCount;
        this.soldCount = soldCount;
        this.linkedEventsCount = linkedEventsCount;
        this.reservationsCount = reservationsCount;
        this.descriptor = new SubscriptionDescriptor(id,
            title,
            description,
            maxAvailable,
            creation,
            onSaleFrom,
            onSaleTo,
            price,
            vat,
            vatStatus,
            currency,
            isPublic,
            organizationId,
            maxEntries,
            validityType,
            validityTimeUnit,
            validityUnits,
            validityFrom,
            validityTo,
            usageType,
            termsAndConditionsUrl,
            privacyPolicyUrl,
            fileBlobId,
            paymentProxies,
            privateKey,
            timeZone,
            supportsTicketsGeneration);
    }

    public BigDecimal getUnitPrice() {
        return MonetaryUtil.centsToUnit(descriptor.getPrice(), descriptor.getCurrency());
    }

    public int getAvailableCount() {
        if(descriptor.getMaxAvailable() > 0) {
            return Math.max(0, descriptor.getMaxAvailable() - soldCount - pendingCount);
        }
        return 0;
    }
}
