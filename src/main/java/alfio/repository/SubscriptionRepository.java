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
import alfio.model.subscription.EventSubscriptionLink;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionTimeUnit;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionValidityType;
import alfio.model.subscription.SubscriptionDescriptorWithStatistics;
import alfio.model.support.Array;
import alfio.model.support.JSONData;
import alfio.model.transaction.PaymentProxy;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@QueryRepository
public interface SubscriptionRepository {

    String FETCH_SUBSCRIPTION_LINK = "select sd.id subscription_descriptor_id, sd.title subscription_descriptor_title, e.id event_id," +
        " e.short_name event_short_name, e.display_name event_display_name, e.currency event_currency, se.price_per_ticket price_per_ticket " +
        " from subscription_event se" +
        " join event e on e.id = se.event_id_fk and e.org_id = :organizationId" +
        " join subscription_descriptor sd on sd.id = se.subscription_descriptor_id_fk and sd.organization_id_fk = :organizationId";

    String INSERT_SUBSCRIPTION_LINK = "insert into subscription_event(event_id_fk, subscription_descriptor_id_fk, price_per_ticket, organization_id_fk)" +
        " values(:eventId, :subscriptionId, :pricePerTicket, :organizationId) on conflict(subscription_descriptor_id_fk, event_id_fk) do update set price_per_ticket = excluded.price_per_ticket";

    @Query("insert into subscription_descriptor (" +
        "id, title, description, max_available, on_sale_from, on_sale_to, price_cts, vat, vat_status, currency, is_public, organization_id_fk, " +
        " max_entries, validity_type, validity_time_unit, validity_units, validity_from, validity_to, usage_type, terms_conditions_url, privacy_policy_url," +
        " file_blob_id_fk, allowed_payment_proxies, private_key, time_zone) " +
        " values(:id, :title::jsonb, :description::jsonb, :maxAvailable, :onSaleFrom, :onSaleTo, :priceCts, :vat, :vatStatus::VAT_STATUS, :currency, " +
        " :isPublic, :organizationId, :maxEntries, :validityType::SUBSCRIPTION_VALIDITY_TYPE, :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, " +
        " :validityUnits, :validityFrom, :validityTo, :usageType::SUBSCRIPTION_USAGE_TYPE, :tcUrl, :privacyPolicyUrl," +
        " :fileBlobId, :allowedPaymentProxies::text[], :privateKey, :timeZone)")
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
                                     @Bind("usageType") SubscriptionUsageType usageType,

                                     @Bind("tcUrl") String termsConditionsUrl,
                                     @Bind("privacyPolicyUrl") String privacyPolicyUrl,
                                     @Bind("fileBlobId") String fileBlobId,
                                     @Bind("allowedPaymentProxies") @Array List<PaymentProxy> allowedPaymentProxies,
                                     @Bind("privateKey") String privateKey,
                                     @Bind("timeZone") String timeZone);

    @Query("update subscription_descriptor set title = :title::jsonb, description = :description::jsonb, max_available = :maxAvailable," +
        " on_sale_from = :onSaleFrom, on_sale_to = :onSaleTo, price_cts = :priceCts, vat = :vat, vat_status = :vatStatus::VAT_STATUS, " +
        " currency = :currency, is_public = :isPublic, max_entries = :maxEntries, validity_type = :validityType::SUBSCRIPTION_VALIDITY_TYPE," +
        " validity_time_unit = :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, validity_units = :validityUnits, validity_from = :validityFrom," +
        " validity_to = :validityTo, usage_type = :usageType::SUBSCRIPTION_USAGE_TYPE," +
        " terms_conditions_url = :tcUrl, privacy_policy_url = :privacyPolicyUrl, file_blob_id_fk = :fileBlobId," +
        " allowed_payment_proxies = :allowedPaymentProxies::text[], time_zone = :timeZone " +
        " where id = :id and organization_id_fk = :organizationId")
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

                                     @Bind("tcUrl") String termsConditionsUrl,
                                     @Bind("privacyPolicyUrl") String privacyPolicyUrl,
                                     @Bind("fileBlobId") String fileBlobId,
                                     @Bind("allowedPaymentProxies") @Array List<PaymentProxy> allowedPaymentProxies,

                                     @Bind("id") UUID id,
                                     @Bind("organizationId") int organizationId,
                                     @Bind("timeZone") String timeZone);

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

    @Query("select * from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where reservation_id_fk = :reservationId)")
    Optional<SubscriptionDescriptor> findDescriptorByReservationId(@Bind("reservationId") String reservationId);

    @Query("select * from subscription_descriptor_statistics where sd_organization_id_fk = :organizationId")
    List<SubscriptionDescriptorWithStatistics> findAllWithStatistics(@Bind("organizationId") int organizationId);

    @Query(INSERT_SUBSCRIPTION_LINK)
    int linkSubscriptionAndEvent(@Bind("subscriptionId") UUID subscriptionId,
                                 @Bind("eventId") int eventId,
                                 @Bind("pricePerTicket") int pricePerTicket,
                                 @Bind("organizationId") int organizationId);

    @Query(type = QueryType.TEMPLATE, value = INSERT_SUBSCRIPTION_LINK)
    String insertSubscriptionEventLink();

    @Query(FETCH_SUBSCRIPTION_LINK + " where se.subscription_descriptor_id_fk = :subscriptionId")
    List<EventSubscriptionLink> findLinkedEvents(@Bind("organizationId") int organizationId,
                                                 @Bind("subscriptionId") UUID id);

    @Query(FETCH_SUBSCRIPTION_LINK + " where se.event_id_fk = :eventId")
    List<EventSubscriptionLink> findLinkedSubscriptions(@Bind("organizationId") int organizationId,
                                                        @Bind("eventId") int eventId);

    @Query("select subscription_descriptor_id_fk from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId")
    List<UUID> findLinkedSubscriptionIds(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId" +
        " and subscription_descriptor_id_fk not in (:descriptorIds)")
    int removeStaleSubscriptions(@Bind("eventId") int eventId,
                                 @Bind("organizationId") int organizationId,
                                 @Bind("descriptorIds") List<UUID> currentDescriptors);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId")
    int removeAllSubscriptionsForEvent(@Bind("eventId") int eventId,
                                       @Bind("organizationId") int organizationId);

    @Query("insert into subscription(id, code, subscription_descriptor_fk, reservation_id_fk, max_usage, usage_count, " +
        " valid_from, valid_to,  organization_id_fk, status, src_price_cts, currency) values (:id, :code, :subscriptionDescriptorId, :reservationId, :maxUsage, 0, :validFrom, :validTo, :organizationId, 'PENDING', :srcPriceCts, :currency)")
    int createSubscription(@Bind("id") UUID id, @Bind("code") String code,
                           @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                           @Bind("reservationId") String reservationId,
                           @Bind("maxUsage") int maxUsage,
                           @Bind("validFrom") ZonedDateTime validFrom, @Bind("validTo") ZonedDateTime validTo,
                           @Bind("srcPriceCts") int srcPriceCts,
                           @Bind("currency") String currency,
                           @Bind("organizationId") int organizationId);

    @Query("delete from subscription where reservation_id_fk in (:expiredReservationIds)")
    int deleteSubscriptionWithReservationId(@Bind("expiredReservationIds") List<String> expiredReservationIds);


    @Query("select * from subscription where reservation_id_fk = :reservationId")
    List<Subscription> findSubscriptionsByReservationId(@Bind("reservationId") String reservationId);

    @Query("select exists(select 1 from subscription_descriptor where id = :id)")
    boolean existsById(@Bind("id") String subscriptionId);

    @Query("select exists (select id from subscription_event where event_id_fk  = :eventId)")
    boolean hasLinkedSubscription(@Bind("eventId") int eventId);

    @Query("select * from subscription where code = :code and email_address = :email for update")
    Subscription findSubscriptionByCodeAndEmailForUpdate(@Bind("code") String code, @Bind("email") String email);

    @Query("update subscription set usage_count = usage_count + 1 where id = :id")
    int increaseUse(@Bind("id") UUID id);

    @Query("select * from subscription where id = (select subscription_id_fk from tickets_reservation where id = :reservationId)")
    Optional<Subscription> findAppliedSubscriptionByReservationId(@Bind("reservationId") String id);

    @Query("update subscription set usage_count = (case when usage_count > 0 then usage_count - 1 else usage_count end) where id in (select subscription_id_fk from tickets_reservation where id in (:reservationIds) and subscription_id_fk is not null)")
    int decrementUseForReservationExpiration(@Bind("reservationIds") List<String> reservationIds);
}
