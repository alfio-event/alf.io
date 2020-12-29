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
package alfio.repository;

import alfio.model.PriceContainer.VatStatus;
import alfio.model.SubscriptionDescriptor;
import alfio.model.SubscriptionDescriptor.SubscriptionTimeUnit;
import alfio.model.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.SubscriptionDescriptor.SubscriptionValidityType;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@QueryRepository
public interface SubscriptionRepository {

    @Query("insert into subscription_descriptor (" +
        "id, title, description, max_available, on_sale_from, on_sale_to, price_cts, vat, vat_status, currency, is_public, organization_id_fk, " +
        " max_entries, validity_type, validity_time_unit, validity_units, validity_from, validity_to, usage_type) " +
        " values(:id, :title::jsonb, :description::jsonb, :maxAvailable, :onSaleFrom, :onSaleTo, :priceCts, :vat, :vatStatus::VAT_STATUS, :currency, " +
        " :isPublic, :organizationId, :maxEntries, :validityType::SUBSCRIPTION_VALIDITY_TYPE, :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, " +
        " :validityUnits, :validityFrom, :validityTo, :usageType::SUBSCRIPTION_USAGE_TYPE)")
    int createSubscriptionDescriptor(@Bind("id") UUID id,
                                     @Bind("title") @JSONData Map<String, String> title,
                                     @Bind("description") @JSONData Map<String, String> description,
                                     @Bind("maxAvailable") int maxAvailable,
                                     @Bind("onSaleFrom") ZonedDateTime onSaleFrom,
                                     @Bind("onSaleTo") ZonedDateTime onSaleTo,
                                     @Bind("priceCts") int priceCts,
                                     @Bind("vat") BigDecimal vat,
                                     @Bind("vatStatus") VatStatus vatStatus,
                                     @Bind("currency") String currency,
                                     @Bind("isPublic") boolean isPublic,
                                     @Bind("organizationId") int organizationId,

                                     @Bind("maxEntries") int maxEntries,
                                     @Bind("validityType") SubscriptionValidityType validityType,
                                     @Bind("validityTimeUnit") SubscriptionTimeUnit validityTimeUnit,
                                     @Bind("validityUnits") Integer validityUnits,
                                     @Bind("validityFrom") ZonedDateTime validityFrom,
                                     @Bind("validityTo") ZonedDateTime validityTo,
                                     @Bind("usageType") SubscriptionUsageType usageType);

    @Query("update subscription_descriptor set title = :title::jsonb, description = :description::jsonb, max_available = :maxAvailable," +
        " on_sale_from = :onSaleFrom, on_sale_to = :onSaleTo, price_cts = :priceCts, vat = :vat, vat_status = :vatStatus::VAT_STATUS, " +
        " currency = :currency, is_public = :isPublic, max_entries = :maxEntries, validity_type = :validityType::SUBSCRIPTION_VALIDITY_TYPE," +
        " validity_time_unit = :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, validity_units = :validityUnits, validity_from = :validityFrom," +
        " validity_to = :validityTo, usage_type = :usageType::SUBSCRIPTION_USAGE_TYPE where id = :id and organization_id_fk = :organizationId")
    int updateSubscriptionDescriptor(@Bind("title") @JSONData Map<String, String> title,
                                     @Bind("description") @JSONData Map<String, String> description,
                                     @Bind("maxAvailable") int maxAvailable,
                                     @Bind("onSaleFrom") ZonedDateTime onSaleFrom,
                                     @Bind("onSaleTo") ZonedDateTime onSaleTo,
                                     @Bind("priceCts") int priceCts,
                                     @Bind("vat") BigDecimal vat,
                                     @Bind("vatStatus") VatStatus vatStatus,
                                     @Bind("currency") String currency,
                                     @Bind("isPublic") boolean isPublic,

                                     @Bind("maxEntries") int maxEntries,
                                     @Bind("validityType") SubscriptionValidityType validityType,
                                     @Bind("validityTimeUnit") SubscriptionTimeUnit validityTimeUnit,
                                     @Bind("validityUnits") Integer validityUnits,
                                     @Bind("validityFrom") ZonedDateTime validityFrom,
                                     @Bind("validityTo") ZonedDateTime validityTo,
                                     @Bind("usageType") SubscriptionUsageType usageType,

                                     @Bind("id") UUID id,
                                     @Bind("organizationId") int organizationId);

    @Query("update subscription_descriptor set is_public = :isPublic where id = :id and organization_id_fk = :organizationId")
    int setPublicStatus(@Bind("id") UUID id, @Bind("organizationId") int organizationId, @Bind("isPublic") boolean isPublic);

    @Query("select * from subscription_descriptor where organization_id_fk = :organizationId order by creation_ts asc")
    List<SubscriptionDescriptor> findAllByOrganizationIds(@Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where is_public = true and (max_entries > 0 or max_entries = -1) and (on_sale_from is null or :from >= on_sale_from)  and (on_sale_to is null or :from <= on_sale_to) order by on_sale_from asc")
    List<SubscriptionDescriptor> findAllActiveAndPublic(@Bind("from") ZonedDateTime from);

    @Query("select * from subscription_descriptor where id = :id and organization_id_fk = :organizationId")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id, @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where id = :id")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id);
}
