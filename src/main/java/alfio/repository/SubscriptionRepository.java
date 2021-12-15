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

import alfio.model.AllocationStatus;
import alfio.model.PriceContainer.VatStatus;
import alfio.model.subscription.*;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionTimeUnit;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionValidityType;
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

    String INSERT_SUBSCRIPTION = "insert into subscription(id, subscription_descriptor_fk, reservation_id_fk, max_usage, " +
        " valid_from, valid_to,  organization_id_fk, status, src_price_cts, currency, max_entries, time_zone) " +
        " values (:id, :subscriptionDescriptorId, :reservationId, :maxUsage, :validFrom, :validTo, :organizationId, :status::ALLOCATION_STATUS, " +
        " :srcPriceCts, :currency, :maxEntries, :timeZone)";

    @Query("insert into subscription_descriptor (" +
        "id, title, description, max_available, on_sale_from, on_sale_to, price_cts, vat, vat_status, currency, is_public, organization_id_fk, " +
        " max_entries, validity_type, validity_time_unit, validity_units, validity_from, validity_to, usage_type, terms_conditions_url, privacy_policy_url," +
        " file_blob_id_fk, allowed_payment_proxies, private_key, time_zone, supports_tickets_generation) " +
        " values(:id, :title::jsonb, :description::jsonb, :maxAvailable, :onSaleFrom, :onSaleTo, :priceCts, :vat, :vatStatus::VAT_STATUS, :currency, " +
        " :isPublic, :organizationId, :maxEntries, :validityType::SUBSCRIPTION_VALIDITY_TYPE, :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, " +
        " :validityUnits, :validityFrom, :validityTo, :usageType::SUBSCRIPTION_USAGE_TYPE, :tcUrl, :privacyPolicyUrl," +
        " :fileBlobId, :allowedPaymentProxies::text[], :privateKey, :timeZone, :supportsTicketsGeneration)")
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
                                     @Bind("timeZone") String timeZone,
                                     @Bind("supportsTicketsGeneration") boolean supportsTicketsGeneration);

    @Query("update subscription_descriptor set title = :title::jsonb, description = :description::jsonb, max_available = :maxAvailable," +
        " on_sale_from = :onSaleFrom, on_sale_to = :onSaleTo, price_cts = :priceCts, vat = :vat, vat_status = :vatStatus::VAT_STATUS, " +
        " currency = :currency, is_public = :isPublic, max_entries = :maxEntries, validity_type = :validityType::SUBSCRIPTION_VALIDITY_TYPE," +
        " validity_time_unit = :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, validity_units = :validityUnits, validity_from = :validityFrom," +
        " validity_to = :validityTo, usage_type = :usageType::SUBSCRIPTION_USAGE_TYPE," +
        " terms_conditions_url = :tcUrl, privacy_policy_url = :privacyPolicyUrl, file_blob_id_fk = :fileBlobId," +
        " allowed_payment_proxies = :allowedPaymentProxies::text[], time_zone = :timeZone, supports_tickets_generation = :supportsTicketsGeneration " +
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
                                     @Bind("timeZone") String timeZone,
                                     @Bind("supportsTicketsGeneration") boolean supportsTicketsGeneration);

    @Query("update subscription_descriptor set is_public = :isPublic where id = :id and organization_id_fk = :organizationId")
    int setPublicStatus(@Bind("id") UUID id, @Bind("organizationId") int organizationId, @Bind("isPublic") boolean isPublic);

    @Query("select * from subscription_descriptor where organization_id_fk = :organizationId order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findAllByOrganizationIds(@Bind("organizationId") int organizationId);

    @Query("select subscription_descriptor.* from subscription_descriptor" +
        " join organization org on subscription_descriptor.organization_id_fk = org.id "+
        " where is_public = true and" +
        " (max_entries > 0 or max_entries = -1) and (on_sale_from is null or :from >= on_sale_from) " +
        " and (on_sale_to is null or :from <= on_sale_to)" +
        " and (:orgSlug is null or org.slug = :orgSlug)"+
        " order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findAllActiveAndPublic(@Bind("from") ZonedDateTime from, @Bind("orgSlug") String organizerSlug);

    @Query("select * from subscription_descriptor where id = :id and organization_id_fk = :organizationId")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id, @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where id = :id")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id);

    @Query("select * from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where reservation_id_fk = :reservationId)")
    Optional<SubscriptionDescriptor> findDescriptorByReservationId(@Bind("reservationId") String reservationId);

    @Query("select * from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where id = :id)")
    SubscriptionDescriptor findDescriptorBySubscriptionId(@Bind("id") UUID subscriptionId);

    @Query("select organization_id_fk from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where id = :id)")
    Optional<Integer> findOrganizationIdForSubscription(@Bind("id") UUID subscriptionId);

    @Query("select * from subscription_descriptor_statistics where sd_organization_id_fk = :organizationId")
    List<SubscriptionDescriptorWithStatistics> findAllWithStatistics(@Bind("organizationId") int organizationId);

    @Query("select exists (select 1 from subscription_event where event_id_fk = :eventId" +
        " and subscription_descriptor_id_fk = :subscriptionDescriptorId::uuid and organization_id_fk = :organizationId)")
    boolean isSubscriptionLinkedToEvent(@Bind("eventId") int eventId,
                                        @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                        @Bind("organizationId") int organizationId);

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

    @Query("select subscription_descriptor_id_fk from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId")
    List<UUID> findLinkedSubscriptionIds(@Bind("eventId") int eventId, @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where organization_id_fk = :organizationId and (on_sale_to is null or on_sale_to > now()) order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findActiveSubscriptionsForOrganization(@Bind("organizationId") int organizationId);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId" +
        " and subscription_descriptor_id_fk not in (:descriptorIds)")
    int removeStaleSubscriptions(@Bind("eventId") int eventId,
                                 @Bind("organizationId") int organizationId,
                                 @Bind("descriptorIds") List<UUID> currentDescriptors);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId")
    int removeAllSubscriptionsForEvent(@Bind("eventId") int eventId,
                                       @Bind("organizationId") int organizationId);

    @Query(type = QueryType.TEMPLATE, value = INSERT_SUBSCRIPTION)
    String batchCreateSubscription();

    @Query(INSERT_SUBSCRIPTION)
    int createSubscription(@Bind("id") UUID id,
                           @Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                           @Bind("reservationId") String reservationId,
                           @Bind("maxUsage") int maxUsage,
                           @Bind("validFrom") ZonedDateTime validFrom,
                           @Bind("validTo") ZonedDateTime validTo,
                           @Bind("srcPriceCts") int srcPriceCts,
                           @Bind("currency") String currency,
                           @Bind("organizationId") int organizationId,
                           @Bind("status") AllocationStatus status,
                           @Bind("maxEntries") int maxEntries,
                           @Bind("timeZone") String timeZone);

    @Query("select id from subscription where subscription_descriptor_fk = :descriptorId and status = 'FREE' limit 1 for update skip locked")
    Optional<UUID> selectFreeSubscription(@Bind("descriptorId") UUID subscriptionDescriptorId);

    @Query("select count(*) from subscription where subscription_descriptor_fk = :descriptorId and status = 'FREE'")
    int countFreeSubscriptionForDescriptor(@Bind("descriptorId") UUID subscriptionDescriptorId);

    @Query("update subscription set reservation_id_fk = :reservationId, status = :status::allocation_status," +
        " src_price_cts = :srcPriceCts where id = :subscriptionId")
    int bindSubscriptionToReservation(@Bind("reservationId") String reservationId, @Bind("srcPriceCts") int price, @Bind("status") AllocationStatus allocationStatus, @Bind("subscriptionId") UUID subscriptionId);

    @Query("delete from subscription where reservation_id_fk in (:expiredReservationIds)")
    int deleteSubscriptionWithReservationId(@Bind("expiredReservationIds") List<String> expiredReservationIds);


    @Query("select * from subscription where reservation_id_fk = :reservationId")
    List<Subscription> findSubscriptionsByReservationId(@Bind("reservationId") String reservationId);

    @Query("select * from subscription where reservation_id_fk = :reservationId for update")
    Optional<Subscription> findFirstSubscriptionByReservationIdForUpdate(@Bind("reservationId") String reservationId);

    @Query("select exists(select 1 from subscription_descriptor where id = :id)")
    boolean existsById(@Bind("id") UUID subscriptionId);

    @Query("select exists (select id from subscription_event where event_id_fk  = :eventId)")
    boolean hasLinkedSubscription(@Bind("eventId") int eventId);

    @Query("select * from subscription where id = :id for update")
    Subscription findSubscriptionByIdForUpdate(@Bind("id") UUID id);

    @Query("select * from subscription where id = :id")
    Subscription findSubscriptionById(@Bind("id") UUID id);

    @Query("select count(*) from subscription where substring(replace(id::text,'-',''), 0, 11) like concat(:partialUuid, '%') and email_address = :email")
    int countSubscriptionByPartialUuidAndEmail(@Bind("partialUuid") String partialUuid, @Bind("email") String email);

    @Query("select count(*) from subscription where substring(replace(id::text,'-',''), 0, 11) like concat(:partialUuid, '%')")
    int countSubscriptionByPartialUuid(@Bind("partialUuid") String partialUuid);

    @Query("select id from subscription where substring(replace(id::text,'-',''), 0, 11) like concat(:partialUuid, '%') and email_address = :email")
    UUID getSubscriptionIdByPartialUuidAndEmail(@Bind("partialUuid") String partialUuid, @Bind("email") String email);

    @Query("select id from subscription where substring(replace(id::text,'-',''), 0, 11) like concat(:partialUuid, '%')")
    UUID getSubscriptionIdByPartialUuid(@Bind("partialUuid") String partialUuid);

    @Query("select * from subscription where id = (select subscription_id_fk from tickets_reservation where id = :reservationId)")
    Optional<Subscription> findAppliedSubscriptionByReservationId(@Bind("reservationId") String id);

    @Query("select count(*) from subscription where id = :id")
    int countSubscriptionById(@Bind("id") UUID fromString);

    @Query("update subscription set status = :status::allocation_status, first_name = :firstName, last_name = :lastName," +
        " email_address = :email, max_entries = :maxEntries, confirmation_ts = :confirmationTs," +
        " validity_from = :validityFrom, validity_to = :validityTo, time_zone = :timeZone " +
        " where reservation_id_fk = :reservationId")
    int confirmSubscription(@Bind("reservationId") String reservationId,
                            @Bind("status") AllocationStatus status,
                            @Bind("firstName") String firstName,
                            @Bind("lastName") String lastName,
                            @Bind("email") String email,
                            @Bind("maxEntries") int maxEntries,
                            @Bind("validityFrom") ZonedDateTime validityFrom,
                            @Bind("validityTo") ZonedDateTime validityTo,
                            @Bind("confirmationTs") ZonedDateTime confirmationTimestamp,
                            @Bind("timeZone") String timeZone);

    @Query("update subscription set first_name = :firstName, last_name = :lastName, email_address = :email," +
        " max_entries = :maxEntries, validity_from = :validityFrom, validity_to = :validityTo where id = :id::uuid")
    int updateSubscription(@Bind("id") UUID subscriptionId,
                           @Bind("firstName") String firstName,
                           @Bind("lastName") String lastName,
                           @Bind("email") String email,
                           @Bind("maxEntries") int maxEntries,
                           @Bind("validityFrom") ZonedDateTime validityFrom,
                           @Bind("validityTo") ZonedDateTime validityTo);

    @Query("update subscription set first_name = :firstName, last_name = :lastName, email_address = :email where reservation_id_fk = :reservationId")
    int assignSubscription(@Bind("reservationId") String reservationId,
                           @Bind("firstName") String firstName,
                           @Bind("lastName") String lastName,
                           @Bind("email") String email);

    @Query("update subscription set status = 'INVALIDATED' where id in (select id from subscription where subscription_descriptor_fk = :descriptorId and status = 'FREE' limit :amount)")
    int invalidateSubscriptions(@Bind("descriptorId") UUID subscriptionDescriptorId, @Bind("amount") int amount);

    @Query("update subscription set status = 'CANCELLED' where reservation_id_fk = :reservationId")
    int cancelSubscriptions(@Bind("reservationId") String reservationId);

    @Query("select * from available_subscriptions_by_event where" +
        " e_end_ts > :nowTs" +
        " and (s_validity_from is null or s_validity_from <= :nowTs)" +
        " and (s_validity_to is null or s_validity_to > :nowTs)" +
        " and (:eventId is null or event_id = :eventId)" +
        " and (:organizationId is null or organization_id = :organizationId)")
    List<AvailableSubscriptionsByEvent> loadAvailableSubscriptionsByEvent(@Bind("eventId") Integer eventId,
                                                                          @Bind("organizationId") Integer organizationId,
                                                                          @Bind("nowTs") ZonedDateTime now);

    @Query("update subscription set src_price_cts = :price where subscription_descriptor_fk = :descriptorId and status = 'FREE'")
    int updatePriceForSubscriptions(@Bind("descriptorId") UUID subscriptionDescriptorId, @Bind("price") int priceCts);
}
