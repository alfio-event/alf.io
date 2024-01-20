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
import alfio.model.metadata.SubscriptionMetadata;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;

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
        " file_blob_id_fk, allowed_payment_proxies, private_key, time_zone, supports_tickets_generation, status) " +
        " values(:id, :title::jsonb, :description::jsonb, :maxAvailable, :onSaleFrom, :onSaleTo, :priceCts, :vat, :vatStatus::VAT_STATUS, :currency, " +
        " :isPublic, :organizationId, :maxEntries, :validityType::SUBSCRIPTION_VALIDITY_TYPE, :validityTimeUnit::SUBSCRIPTION_TIME_UNIT, " +
        " :validityUnits, :validityFrom, :validityTo, :usageType::SUBSCRIPTION_USAGE_TYPE, :tcUrl, :privacyPolicyUrl," +
        " :fileBlobId, :allowedPaymentProxies::text[], :privateKey, :timeZone, :supportsTicketsGeneration, 'ACTIVE')")
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

    @Query("update subscription_descriptor set status = 'NOT_ACTIVE' where id = :id and organization_id_fk = :organizationId")
    int deactivateDescriptor(@Bind("id") UUID id, @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where organization_id_fk = :organizationId" +
        " and status = 'ACTIVE'" +
        " order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findAllByOrganizationIds(@Bind("organizationId") int organizationId);

    @Query("select subscription_descriptor.* from subscription_descriptor" +
        " join organization org on subscription_descriptor.organization_id_fk = org.id "+
        " where status = 'ACTIVE' and is_public = true and" +
        " (max_entries > 0 or max_entries = -1) and (on_sale_from is null or :from >= on_sale_from) " +
        " and (on_sale_to is null or :from <= on_sale_to)" +
        " and (:orgSlug is null or org.slug = :orgSlug)"+
        " order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findAllActiveAndPublic(@Bind("from") ZonedDateTime from, @Bind("orgSlug") String organizerSlug);

    @Query("select * from subscription_descriptor where status = 'ACTIVE' and id = :id and organization_id_fk = :organizationId")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id, @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where id = :id and status = 'ACTIVE'")
    Optional<SubscriptionDescriptor> findOne(@Bind("id") UUID id);

    @Query("select organization_id_fk from subscription_descriptor where status = 'ACTIVE' and id = :id")
    Optional<Integer> findOrganizationIdForDescriptor(@Bind("id") UUID id);

    @Query("select count(*) from subscription_descriptor where status = 'ACTIVE' and id in(:ids) and organization_id_fk = :organizationId")
    Integer countDescriptorsBelongingToOrganization(@Bind("ids") Collection<UUID> ids,
                                                    @Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where reservation_id_fk = :reservationId) and status = 'ACTIVE'")
    Optional<SubscriptionDescriptor> findDescriptorByReservationId(@Bind("reservationId") String reservationId);

    @Query("select * from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where id = :id) and status = 'ACTIVE'")
    SubscriptionDescriptor findDescriptorBySubscriptionId(@Bind("id") UUID subscriptionId);

    @Query("select organization_id_fk from subscription_descriptor where id = (select subscription_descriptor_fk from subscription where id = :id) and status = 'ACTIVE'")
    Optional<Integer> findOrganizationIdForSubscription(@Bind("id") UUID subscriptionId);

    @Query("select * from subscription_descriptor_statistics where sd_organization_id_fk = :organizationId")
    List<SubscriptionDescriptorWithStatistics> findAllWithStatistics(@Bind("organizationId") int organizationId);

    @Query("select * from subscription_descriptor_statistics where sd_organization_id_fk = :organizationId and sd_id = :subscriptionDescriptorId")
    Optional<SubscriptionDescriptorWithStatistics> findOneWithStatistics(@Bind("subscriptionDescriptorId") UUID subscriptionDescriptorId,
                                                                         @Bind("organizationId") int organizationId);

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

    @Query("select * from subscription_descriptor where status = 'ACTIVE' and organization_id_fk = :organizationId" +
        " and (on_sale_to is null or on_sale_to > now()) order by on_sale_from, on_sale_to nulls last")
    List<SubscriptionDescriptor> findActiveSubscriptionsForOrganization(@Bind("organizationId") int organizationId);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId" +
        " and subscription_descriptor_id_fk not in (:descriptorIds)")
    int removeStaleSubscriptions(@Bind("eventId") int eventId,
                                 @Bind("organizationId") int organizationId,
                                 @Bind("descriptorIds") List<UUID> currentDescriptors);

    @Query("delete from subscription_event where subscription_descriptor_id_fk = :subscriptionId and organization_id_fk = :organizationId" +
        " and event_id_fk not in (:eventIds)")
    int removeStaleEvents(@Bind("subscriptionId") UUID subscriptionId,
                          @Bind("organizationId") int organizationId,
                          @Bind("eventIds") List<Integer> eventIds);

    @Query("delete from subscription_event where event_id_fk = :eventId and organization_id_fk = :organizationId")
    int removeAllSubscriptionsForEvent(@Bind("eventId") int eventId,
                                       @Bind("organizationId") int organizationId);

    @Query("delete from subscription_event where subscription_descriptor_id_fk = :subscriptionId and organization_id_fk = :organizationId")
    int removeAllLinksForSubscription(@Bind("subscriptionId") UUID subscriptionId,
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

    @Query("select exists(select 1 from subscription_descriptor where id = :id and status = 'ACTIVE')")
    boolean existsById(@Bind("id") UUID subscriptionId);

    @Query("select exists (select id from subscription_event where event_id_fk  = :eventId)")
    boolean hasLinkedSubscription(@Bind("eventId") int eventId);

    @Query("select exists (select id from subscription_event where subscription_descriptor_id_fk  = :descriptorId::uuid)")
    boolean hasLinkedEvents(@Bind("descriptorId") UUID subscriptionDescriptorId);

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

    @Query("update subscription set status = 'CANCELLED' where reservation_id_fk = :reservationId" +
        " and id = :subscriptionId and subscription_descriptor_fk = :descriptorId")
    int cancelSubscription(@Bind("reservationId") String reservationId,
                           @Bind("subscriptionId") UUID subscriptionId,
                           @Bind("descriptorId") UUID descriptorId);

    NamedParameterJdbcTemplate getJdbcTemplate();

    default Map<Integer, List<AvailableSubscriptionsByEvent>> loadAvailableSubscriptionsByEvent(@Bind("eventId") Integer eventId,
                                                                          @Bind("organizationId") Integer organizationId) {
        Map<Integer, List<AvailableSubscriptionsByEvent>> result = new TreeMap<>();
        var paramSource = new MapSqlParameterSource("eventId", eventId)
            .addValue("organizationId", organizationId);
        getJdbcTemplate().query("with usage_by_subscription_id as ( " +
            "        select s.id subscription_id, " +
            "               sum(case when t.subscription_id_fk is not null then 1 else 0 end) usage " +
            "        from subscription s " +
            "                 left join tickets_reservation t on t.subscription_id_fk = s.id " +
            "        group by 1 " +
            "    ), subscription_expiration as ( " +
            "        select id, " +
            "               coalesce(s.validity_from, 'yesterday'::timestamp) inception, " +
            "               coalesce(s.validity_to, 'tomorrow'::timestamp) expiration " +
            "        from subscription s " +
            "    ), subscription_additional as (" +
            "       select subscription_id_fk, jsonb_agg(jsonb_build_object('name', field_name, 'value', to_jsonb(field_value))) fields" +
        "           from field_value_w_additional where context = 'SUBSCRIPTION' group by 1" +
            "    ) " +
            "    select e.id event_id, " +
            "           e.org_id organization_id, " +
            "           s.id as subscription_id, " +
            "           s.email_address as email_address, " +
            "           s.first_name as first_name, " +
            "           s.last_name as last_name, " +
            "           r.user_language as user_language," +
            "           r.email_address as reservation_email," +
            "           fv.fields as additional_fields " +
            "    from event e " +
            "             join subscription_event se on se.event_id_fk = e.id " +
            "             join subscription_descriptor sd on se.subscription_descriptor_id_fk = sd.id " +
            "             join subscription s on sd.id = s.subscription_descriptor_fk " +
            "             join usage_by_subscription_id u on s.id = u.subscription_id " +
            "             join subscription_expiration exp on s.id = exp.id " +
            "             join tickets_reservation r on r.id = s.reservation_id_fk " +
            "             left join subscription_additional fv on s.id = fv.subscription_id_fk" +
            "    where e.end_ts > now() " + // make sure that the event has not expired
            "      and (:eventId::int is null or e.id = :eventId::int)" +
            "      and (:organizationId::int is null or e.org_id = :organizationId::int)" +
            "      and s.status = 'ACQUIRED' " +
            "      and not exists(select id from tickets_reservation tr where tr.subscription_id_fk = s.id and tr.event_id_fk = e.id) " +
            "      and sd.supports_tickets_generation is TRUE " +
            "      and exp.inception <= now() " +
            "      and exp.expiration > now() " +
            "      and (s.max_entries = -1 or s.max_entries > u.usage) " +
            "      and (select count(*) from ticket_category where event_id = e.id and tc_status = 'ACTIVE') > 0" +
            "    order by e.id", paramSource, rse -> {
            int eId = rse.getInt("event_id");
            if (!result.containsKey(eId)) {
                result.put(eId, new ArrayList<>());
            }
            result.get(eId).add(new AvailableSubscriptionsByEvent(
                eId,
                rse.getInt("organization_id"),
                rse.getObject("subscription_id", UUID.class),
                rse.getString("email_address"),
                rse.getString("first_name"),
                rse.getString("last_name"),
                rse.getString("user_language"),
                rse.getString("reservation_email"),
                rse.getString("additional_fields")
            ));
        });
        return result;
    }

    @Query("update subscription set src_price_cts = :price where subscription_descriptor_fk = :descriptorId and status = 'FREE'")
    int updatePriceForSubscriptions(@Bind("descriptorId") UUID subscriptionDescriptorId, @Bind("price") int priceCts);

    @Query("update subscription set max_entries = :maxEntries, max_usage = :maxEntries where subscription_descriptor_fk = :descriptorId")
    int updateMaxEntriesForSubscriptions(@Bind("descriptorId") UUID subscriptionDescriptorId, @Bind("maxEntries") int maxEntries);

    @Query("update subscription set metadata = :metadata::jsonb where id = :id")
    int setMetadataForSubscription(@Bind("id") UUID subscriptionId, @Bind("metadata") @JSONData SubscriptionMetadata metadata);

    @Query("select metadata from subscription where id = :id")
    @JSONData
    SubscriptionMetadata getSubscriptionMetadata(@Bind("id") UUID subscriptionId);
}
