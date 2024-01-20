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

import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

@QueryRepository
public interface PurchaseContextFieldRepository extends FieldRepository {

    String INSERT_VALUE = "insert into purchase_context_field_value(ticket_id_fk, subscription_id_fk, organization_id_fk, field_configuration_id_fk, field_value, context) values (:ticketId, :subscriptionId::uuid, :organizationId, :fieldConfigurationId, :value, :context::ADDITIONAL_FIELD_CONTEXT)";
    String ADDITIONAL_SERVICE_FIELD_VALUE_COLS = "field_name, field_value, additional_service_id_fk, ticket_id_fk, field_configuration_id_fk, additional_service_item_id_fk";
    String FIELD_VALUE_COLUMNS = "ticket_id_fk, subscription_id_fk, additional_service_item_id_fk, field_configuration_id_fk, field_name, field_value, context";
    String FIND_ALL = "select "+ FIELD_VALUE_COLUMNS +" from field_value_w_additional";
    String FIND_ALL_BY_TICKET_ID = FIND_ALL + " where ticket_id_fk = :ticketId";
    String PURCHASE_CONTEXT_MATCHER = "(:eventId is null or event_id_fk = :eventId) and (:subscriptionId::uuid is null or subscription_descriptor_id_fk = :subscriptionId::uuid)";
    String TICKET_ID_OR_SUBSCRIPTION_ID = "((:ticketId::integer is not null or :subscriptionId::uuid is not null) and (:ticketId::integer is null or ticket_id_fk = :ticketId) and (:subscriptionId::uuid is null or subscription_id_fk = :subscriptionId::uuid))";

    @Query("select count(*) from field_value_w_additional where ticket_id_fk = :ticketId and field_value is not null and field_value <> ''")
    Integer countFilledOptionalData(@Bind("ticketId") int id);

    @Query(FIND_ALL_BY_TICKET_ID)
    List<PurchaseContextFieldValue> findAllByTicketId(@Bind("ticketId") int id);

    @Query(FIND_ALL_BY_TICKET_ID + " and context = :context::ADDITIONAL_FIELD_CONTEXT")
    List<PurchaseContextFieldValue> findAllForContextByTicketId(@Bind("ticketId") int id,
                                                                @Bind("context") PurchaseContextFieldConfiguration.Context context);

    @Query("select "+FIELD_VALUE_COLUMNS+", description from all_ticket_field_values " +
        " where ticket_id_fk = :ticketId and field_name in (:fieldNames)")
    List<FieldValueAndDescription> findValueForTicketId(@Bind("ticketId") int id, @Bind("fieldNames") Set<String> fieldNames);

    @Query("update purchase_context_field_value set field_value = :value where " + TICKET_ID_OR_SUBSCRIPTION_ID +
        " and field_configuration_id_fk = :fieldConfigurationId")
    int updateValue(@Bind("ticketId") Integer ticketId,
                    @Bind("subscriptionId") UUID subscriptionId,
                    @Bind("fieldConfigurationId") long fieldConfigurationId,
                    @Bind("value") String value);

    @Query(INSERT_VALUE)
    int insertValue(@Bind("ticketId") Integer ticketId,
                    @Bind("subscriptionId") UUID subscriptionId,
                    @Bind("organizationId") int organizationId,
                    @Bind("fieldConfigurationId") long fieldConfigurationId,
                    @Bind("value") String value,
                    @Bind("context") PurchaseContextFieldConfiguration.Context context);

    @Query(type = QueryType.TEMPLATE,
        value = "insert into purchase_context_field_value(context, additional_service_item_id_fk, field_configuration_id_fk, field_value, organization_id_fk)" +
            " values ('ADDITIONAL_SERVICE'::ADDITIONAL_FIELD_CONTEXT, :additionalServiceItemId, :fieldConfigurationId, :value, :organizationId)")
    String batchInsertAdditionalItemsFields();

    @Query("delete from purchase_context_field_value where " + TICKET_ID_OR_SUBSCRIPTION_ID +" and field_configuration_id_fk = :fieldConfigurationId")
    int deleteValue(@Bind("ticketId") Integer ticketId, @Bind("subscriptionId") UUID subscriptionId, @Bind("fieldConfigurationId") long fieldConfigurationId);

    @Query("delete from purchase_context_field_value where ticket_id_fk = :ticketId")
    int deleteAllValuesForTicket(@Bind("ticketId") int ticketId);

    @Query("delete from purchase_context_field_value where ticket_id_fk in (:ticketIds)")
    int deleteAllValuesForTicketIds(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("delete from purchase_context_field_value where subscription_id_fk = :subscriptionId")
    int deleteAllValuesForSubscriptionId(@Bind("subscriptionId") UUID subscriptionId);

    @Query("delete from purchase_context_field_value fv using ticket t, additional_service_item ai" +
        " where fv.context = 'ADDITIONAL_SERVICE' and fv.additional_service_item_id_fk = ai.id" +
        " and ai.ticket_id_fk = t.id and t.id in (:ticketIds) and t.event_id = :eventId")
    int deleteAllValuesForAdditionalItems(@Bind("ticketIds") Collection<Integer> ticketIds,
                                          @Bind("eventId") int eventId);

    @Query("delete from purchase_context_field_value fv using ticket t where t.id = fv.ticket_id_fk and t.tickets_reservation_id in(:reservationIds)")
    int deleteAllTicketValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    @Query("delete from purchase_context_field_value fv using additional_service_item asi where asi.id = fv.additional_service_item_id_fk and asi.tickets_reservation_uuid in(:reservationIds)")
    int deleteAllAdditionalItemsValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    @Query("delete from purchase_context_field_value fv using subscription s where s.id = fv.subscription_id_fk and s.reservation_id_fk in(:reservationIds)")
    int deleteAllSubscriptionValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    default int deleteAllValuesForReservations(List<String> reservationIds) {
        return deleteAllTicketValuesForReservations(reservationIds) + deleteAllAdditionalItemsValuesForReservations(reservationIds) + deleteAllSubscriptionValuesForReservations(reservationIds);
    }

    @Query("select field_configuration_id_fk, field_locale, description, purchase_context_field_configuration.field_name as field_name from purchase_context_field_description" +
        " inner join purchase_context_field_configuration on field_configuration_id_fk = id where field_locale = :locale and event_id_fk = :eventId")
    List<PurchaseContextFieldDescription> findDescriptionsForLocale(@Bind("eventId") int eventId, @Bind("locale") String locale);

    @Query("select field_configuration_id_fk, field_locale, description, purchase_context_field_configuration.field_name as field_name from purchase_context_field_description" +
        " inner join purchase_context_field_configuration on field_configuration_id_fk = id" +
        " where "+ PURCHASE_CONTEXT_MATCHER)
    List<PurchaseContextFieldDescription> findAllDescriptions(@Bind("eventId") Integer eventId, @Bind("subscriptionId") UUID subscriptionDescriptorId);

    @Query("SELECT field_name FROM purchase_context_field_configuration" +
        " inner join event on event.id = event_id_fk" +
        " where short_name = :eventShortName order by field_order asc ")
    List<String> findFieldsForEvent(@Bind("eventShortName") String eventShortName);

    @Query("select field_name, field_value from field_value_w_additional where ticket_id_fk = :ticketId")
    List<FieldNameAndValue> findNameAndValue(@Bind("ticketId") int ticketId);

    @Query("select field_name, field_value from field_value_w_additional where subscription_id_fk = :subscriptionId")
    List<FieldNameAndValue> findNameAndValue(@Bind("subscriptionId") UUID subscriptionId);

    @Query(FIND_ALL + " where ticket_id_fk in (:ticketIds)")
    List<PurchaseContextFieldValue> findAllValuesByTicketIds(@Bind("ticketIds") Collection<Integer> ticketIds);

    @Query(FIND_ALL + " where subscription_id_fk in (:subscriptionIds)")
    List<PurchaseContextFieldValue> findAllValuesBySubscriptionIds(@Bind("subscriptionIds") Collection<UUID> subscriptionIds);

    default void updateOrInsert(Map<String, List<String>> values, PurchaseContext purchaseContext, Integer ticketId, UUID subscriptionId) {
        Map<String, PurchaseContextFieldValue> toUpdate;
        values = Optional.ofNullable(values).orElseGet(Collections::emptyMap);
        List<PurchaseContextFieldConfiguration> additionalFields;
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            Event event = (Event) purchaseContext;
            additionalFields = findAdditionalFieldsForEvent(event.getId());
            toUpdate = findAllByTicketIdGroupedByName(ticketId, event.supportsLinkedAdditionalServices());
        } else {
            additionalFields = findAdditionalFieldsForSubscriptionDescriptor(((SubscriptionDescriptor)purchaseContext).getId());
            toUpdate = collectByName(findAllValuesBySubscriptionIds(List.of(subscriptionId)));
        }
        var readOnlyFields = additionalFields.stream().filter(PurchaseContextFieldConfiguration::isReadOnly).map(PurchaseContextFieldConfiguration::getName).collect(Collectors.toSet());
        Map<String, Long> fieldNameToId = additionalFields.stream().collect(Collectors.toMap(PurchaseContextFieldConfiguration::getName, PurchaseContextFieldConfiguration::getId));

        values.forEach((fieldName, fieldValues) -> {
            var fieldValue = getFieldValueJson(fieldValues);

            boolean isNotBlank = StringUtils.isNotBlank(fieldValue);
            if(toUpdate.containsKey(fieldName)) {
                if(!readOnlyFields.contains(fieldName)) {
                    PurchaseContextFieldValue field = toUpdate.get(fieldName);
                    if(isNotBlank) {
                        updateValue(field.getTicketId(), field.getSubscriptionId(), field.getFieldConfigurationId(), fieldValue);
                    } else {
                        deleteValue(field.getTicketId(), field.getSubscriptionId(), field.getFieldConfigurationId());
                    }
                }
            } else if(fieldNameToId.containsKey(fieldName) && isNotBlank) {
                var context = ticketId != null ? PurchaseContextFieldConfiguration.Context.ATTENDEE : PurchaseContextFieldConfiguration.Context.SUBSCRIPTION;
                insertValue(ticketId, subscriptionId, purchaseContext.getOrganizationId(), fieldNameToId.get(fieldName), fieldValue, context);
            }
        });
    }

    default String getFieldValueJson(List<String> fieldValues) {
        String fieldValue;
        if(fieldValues.size() == 1) {
            fieldValue = fieldValues.get(0);
        } else if(fieldValues.stream().anyMatch(StringUtils::isNotBlank)) {
            fieldValue = Json.toJson(fieldValues);
        } else {
            fieldValue = "";
        }
        return fieldValue;
    }

    default Map<String, PurchaseContextFieldValue> findAllByTicketIdGroupedByName(Integer id, boolean eventSupportsLink) {
        List<PurchaseContextFieldValue> values;
        if (eventSupportsLink) {
            values = findAllForContextByTicketId(id, PurchaseContextFieldConfiguration.Context.ATTENDEE);
        } else {
            values = findAllByTicketId(id);
        }
        return collectByName(values);
    }

    private Map<String, PurchaseContextFieldValue> collectByName(List<PurchaseContextFieldValue> values) {
        return values.stream()
            .collect(Collectors.toMap(PurchaseContextFieldValue::getName, Function.identity()));
    }

    default boolean hasOptionalData(int ticketId) {
        return countFilledOptionalData(ticketId) > 0;
    }


    @Query("select * from purchase_context_field_configuration where event_id_fk = :eventId order by field_order asc")
    List<PurchaseContextFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventId") int eventId);

    default Map<Integer, Set<String>> findAdditionalFieldNamesForEvents(Collection<Integer> eventIds) {
        return getJdbcTemplate().query("select event_id_fk, field_name from purchase_context_field_configuration where event_id_fk = :eventIds",
            new MapSqlParameterSource("eventIds", eventIds),
            (ResultSet rs) -> {
                var result = new HashMap<Integer, Set<String>>();
                while (rs.next()) {
                    Integer eventId = rs.getInt(1);
                    if (!result.containsKey(eventId)) {
                        result.put(eventId, new HashSet<>());
                    }
                    result.get(eventId).add(rs.getString(2));
                }
                return result;
            });
    }

    @Query("select * from purchase_context_field_configuration where subscription_descriptor_id_fk = :subscriptionId::uuid order by field_order asc")
    List<PurchaseContextFieldConfiguration> findAdditionalFieldsForSubscriptionDescriptor(@Bind("subscriptionId") UUID subscriptionDescriptorId);

    @Query("select * from purchase_context_field_configuration where event_id_fk = :eventId" +
        " and field_type = :type order by field_order asc")
    List<PurchaseContextFieldConfiguration> findAdditionalFieldsOfTypeForEvent(@Bind("eventId") int eventId, @Bind("type") String type);

    @Query("select * from purchase_context_field_configuration where id = :id")
    PurchaseContextFieldConfiguration findById(@Bind("id") long id);

    @Query("update purchase_context_field_configuration set field_order = :order where id = :id")
    int updateFieldOrder(@Bind("id") long id, @Bind("order") int order);

    @Query("select purchase_context_field_configuration.* from purchase_context_field_configuration" +
        " inner join event on event.id = event_id_fk" +
        " where short_name = :eventShortName order by field_order asc")
    List<PurchaseContextFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventShortName") String eventName);

    @Query("select count(*) from purchase_context_field_configuration where event_id_fk = :eventId")
    Integer countAdditionalFieldsForEvent(@Bind("eventId") int eventId);

    @Query("select max(field_order) from purchase_context_field_configuration where " + PURCHASE_CONTEXT_MATCHER)
    Integer findMaxOrderValue(@Bind("eventId") Integer eventId, @Bind("subscriptionId") UUID subscriptionId);

    default Map<Long, PurchaseContextFieldDescription> findTranslationsFor(Locale locale, int eventId) {
        return findDescriptionsForLocale(eventId, locale.getLanguage()).stream().collect(Collectors.toMap(PurchaseContextFieldDescription::getFieldConfigurationId, Function.identity()));
    }

    default Map<String, String> findAllValuesForTicketId(int ticketId) {
        return findNameAndValue(ticketId).stream().filter(t -> t.getName() != null && t.getValue() != null).collect(Collectors.toMap(FieldNameAndValue::getName, FieldNameAndValue::getValue));
    }

    // required for deleting a field

    @Query("delete from purchase_context_field_value where field_configuration_id_fk = :fieldConfigurationId")
	int deleteValues(@Bind("fieldConfigurationId") long ticketFieldConfigurationId);

    @Query("delete from purchase_context_field_description where field_configuration_id_fk = :fieldConfigurationId")
	int deleteDescription(@Bind("fieldConfigurationId") long ticketFieldConfigurationId);

    @Query("delete from purchase_context_field_configuration where id = :fieldConfigurationId")
	int deleteField(@Bind("fieldConfigurationId") long ticketFieldConfigurationId);

    @Query("select field_value as name, count(*) as count from field_value_w_additional where field_configuration_id_fk = :configurationId group by field_value")
    List<RestrictedValueStats.RestrictedValueCount> getValueStats(@Bind("configurationId") long configurationId);

    default List<RestrictedValueStats> retrieveStats(long configurationId) {
        PurchaseContextFieldConfiguration configuration = findById(configurationId);
        Map<String, Integer> valueStats = getValueStats(configurationId).stream().collect(Collectors.toMap(RestrictedValueStats.RestrictedValueCount::getName, RestrictedValueStats.RestrictedValueCount::getCount));
        int total = valueStats.values().stream().mapToInt(i -> i).sum();
        if (configuration.isCountryField()) {
            // no restricted values
            var descComparator = Entry.<String, Integer>comparingByValue().reversed();
            return valueStats.entrySet().stream()
                .sorted(descComparator)
                .map(entry -> {
                    int count = entry.getValue();
                    return new RestrictedValueStats(entry.getKey(),
                        count,
                        new BigDecimal(count).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP).multiply(MonetaryUtil.HUNDRED).intValue()
                    );
                }).collect(Collectors.toList());
        }
        return configuration.getRestrictedValues().stream()
            .map(name -> {
                int count = valueStats.getOrDefault(name, 0);
                return new RestrictedValueStats(name, count, total == 0 ? 0 : new BigDecimal(count).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP).multiply(MonetaryUtil.HUNDRED).intValue());
            }).collect(Collectors.toList());

    }

    @Query("select " + ADDITIONAL_SERVICE_FIELD_VALUE_COLS +
        " from additional_item_field_value_with_ticket_id" +
        "  where ticket_id_fk = :ticketId" +
        "  and additional_service_id_fk in (:additionalServiceIds)")
    List<AdditionalServiceFieldValue> loadTicketFieldsForAdditionalService(@Bind("ticketId") int ticketId,
                                                                           @Bind("additionalServiceIds") List<Integer> additionalServiceIds);

    @Query("select " + ADDITIONAL_SERVICE_FIELD_VALUE_COLS + " from additional_item_field_value_with_ticket_id where ticket_id_fk in(:ticketIds)")
    List<AdditionalServiceFieldValue> findAdditionalServicesValueByTicketIds(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("select " + ADDITIONAL_SERVICE_FIELD_VALUE_COLS + " from additional_item_field_value_with_ticket_id where additional_service_item_id_fk in (:itemIds)")
    List<AdditionalServiceFieldValue> findAdditionalServicesValueByItemIds(@Bind("itemIds") List<Integer> itemIds);

    @Query("select count(*) from purchase_context_field_configuration where id in (:additionalFieldIds) and "+PURCHASE_CONTEXT_MATCHER)
    int countMatchingAdditionalFieldsForPurchaseContext(@Bind("eventId") Integer eventId,
                                                        @Bind("subscriptionId") UUID subscriptionDescriptorId,
                                                        @Bind("additionalFieldIds") Set<Long> additionalFieldIds);

    NamedParameterJdbcTemplate getJdbcTemplate();

}
