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
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;
import ch.digitalfondue.npjt.QueryType;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

@QueryRepository
public interface TicketFieldRepository extends FieldRepository {

    String INSERT_VALUE = "insert into purchase_context_field_value(ticket_id_fk, organization_id_fk, field_configuration_id_fk, field_value, context) values (:ticketId, :organizationId, :fieldConfigurationId, :value, :context::ADDITIONAL_FIELD_CONTEXT)";
    String FIND_ALL_BY_TICKET_ID = "select a.ticket_id_fk, a.field_configuration_id_fk, a.field_name, a.field_value from ticket_field_value_w_additional a where a.ticket_id_fk = :ticketId";
    String ADDITIONAL_SERVICE_FIELD_VALUE_COLS = "field_name, field_value, additional_service_id_fk, ticket_id_fk, field_configuration_id_fk, additional_service_item_id_fk";

    @Query("select count(*) from ticket_field_value_w_additional where ticket_id_fk = :ticketId and field_value is not null and field_value <> ''")
    Integer countFilledOptionalData(@Bind("ticketId") int id);

    @Query(FIND_ALL_BY_TICKET_ID)
    List<TicketFieldValue> findAllByTicketId(@Bind("ticketId") int id);

    @Query(FIND_ALL_BY_TICKET_ID + " and context = :context::ADDITIONAL_FIELD_CONTEXT")
    List<TicketFieldValue> findAllForContextByTicketId(@Bind("ticketId") int id, @Bind("context") String context);

    @Query("select ticket_id_fk, field_configuration_id_fk, field_name, field_value, description from all_ticket_field_values " +
        " where ticket_id_fk = :ticketId and field_name in (:fieldNames)")
    List<TicketFieldValueAndDescription> findValueForTicketId(@Bind("ticketId") int id, @Bind("fieldNames") Set<String> fieldNames);

    @Query("update purchase_context_field_value set field_value = :value where ticket_id_fk = :ticketId and field_configuration_id_fk = :fieldConfigurationId")
    int updateValue(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") long fieldConfigurationId, @Bind("value") String value);

    @Query(INSERT_VALUE)
    int insertValue(@Bind("ticketId") int ticketId,
                    @Bind("organizationId") int organizationId,
                    @Bind("fieldConfigurationId") long fieldConfigurationId,
                    @Bind("value") String value,
                    @Bind("context") TicketFieldConfiguration.Context context);

    @Query(type = QueryType.TEMPLATE,
        value = "insert into purchase_context_field_value(context, additional_service_item_id_fk, field_configuration_id_fk, field_value, organization_id_fk)" +
            " values ('ADDITIONAL_SERVICE'::ADDITIONAL_FIELD_CONTEXT, :additionalServiceItemId, :fieldConfigurationId, :value, :organizationId)")
    String batchInsertAdditionalItemsFields();

    @Query("delete from purchase_context_field_value where ticket_id_fk = :ticketId and field_configuration_id_fk = :fieldConfigurationId")
    int deleteValue(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") long fieldConfigurationId);

    @Query("delete from purchase_context_field_value where ticket_id_fk = :ticketId")
    int deleteAllValuesForTicket(@Bind("ticketId") int ticketId);

    @Query("delete from purchase_context_field_value where ticket_id_fk in (:ticketIds)")
    int deleteAllValuesForTicketIds(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("delete from purchase_context_field_value fv using ticket t, additional_service_item ai" +
        " where fv.context = 'ADDITIONAL_SERVICE' and fv.additional_service_item_id_fk = ai.id" +
        " and ai.ticket_id_fk = t.id and t.id in (:ticketIds) and t.event_id = :eventId")
    int deleteAllValuesForAdditionalItems(@Bind("ticketIds") Collection<Integer> ticketIds,
                                          @Bind("eventId") int eventId);

    @Query("delete from purchase_context_field_value fv using ticket t where t.id = fv.ticket_id_fk and t.tickets_reservation_id in(:reservationIds)")
    int deleteAllTicketValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    @Query("delete from purchase_context_field_value fv using additional_service_item asi where asi.id = fv.additional_service_item_id_fk and asi.tickets_reservation_uuid in(:reservationIds)")
    int deleteAllAdditionalItemsValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    default int deleteAllValuesForReservations(List<String> reservationIds) {
        return deleteAllTicketValuesForReservations(reservationIds) + deleteAllAdditionalItemsValuesForReservations(reservationIds);
    }

    @Query("select field_configuration_id_fk, field_locale, description from purchase_context_field_description" +
        " inner join purchase_context_field_configuration on field_configuration_id_fk = id where field_locale = :locale and event_id_fk = :eventId")
    List<TicketFieldDescription> findDescriptions(@Bind("eventId") int eventId, @Bind("locale") String locale);

    @Query("select purchase_context_field_description.* from purchase_context_field_description" +
        " inner join purchase_context_field_configuration on field_configuration_id_fk = id" +
        " inner join event on event.id = event_id_fk" +
        " where short_name = :eventShortName")
    List<TicketFieldDescription> findDescriptions(@Bind("eventShortName") String eventShortName);

    @Query("SELECT field_name FROM purchase_context_field_configuration" +
        " inner join event on event.id = event_id_fk" +
        " where short_name = :eventShortName order by field_order asc ")
    List<String> findFieldsForEvent(@Bind("eventShortName") String eventShortName);

    @Query("select field_name, field_value from ticket_field_value_w_additional where ticket_id_fk = :ticketId")
    List<FieldNameAndValue> findNameAndValue(@Bind("ticketId") int ticketId);

    @Query("select ticket_id_fk, field_configuration_id_fk, field_name, field_value from ticket_field_value_w_additional where ticket_id_fk in (:ticketIds)")
    List<TicketFieldValue> findAllValuesByTicketIds(@Bind("ticketIds") Collection<Integer> ticketIds);

    default void updateOrInsert(Map<String, List<String>> values, int ticketId, int eventId, int organizationId, boolean eventSupportsLink) {
        Map<String, TicketFieldValue> toUpdate = findAllByTicketIdGroupedByName(ticketId, eventSupportsLink);
        values = Optional.ofNullable(values).orElseGet(Collections::emptyMap);
        var additionalFieldsForEvent = findAdditionalFieldsForEvent(eventId);
        var readOnlyFields = additionalFieldsForEvent.stream().filter(TicketFieldConfiguration::isReadOnly).map(TicketFieldConfiguration::getName).collect(Collectors.toSet());
        Map<String, Long> fieldNameToId = additionalFieldsForEvent.stream().collect(Collectors.toMap(TicketFieldConfiguration::getName, TicketFieldConfiguration::getId));

        values.forEach((fieldName, fieldValues) -> {
            var fieldValue = getFieldValueJson(fieldValues);

            boolean isNotBlank = StringUtils.isNotBlank(fieldValue);
            if(toUpdate.containsKey(fieldName)) {
                if(!readOnlyFields.contains(fieldName)) {
                    TicketFieldValue field = toUpdate.get(fieldName);
                    if(isNotBlank) {
                        updateValue(field.getTicketId(), field.getFieldConfigurationId(), fieldValue);
                    } else {
                        deleteValue(field.getTicketId(), field.getFieldConfigurationId());
                    }
                }
            } else if(fieldNameToId.containsKey(fieldName) && isNotBlank) {
                insertValue(ticketId, organizationId, fieldNameToId.get(fieldName), fieldValue, TicketFieldConfiguration.Context.ATTENDEE);
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

    default Map<String, TicketFieldValue> findAllByTicketIdGroupedByName(int id, boolean eventSupportsLink) {
        List<TicketFieldValue> values;
        if (eventSupportsLink) {
            values = findAllForContextByTicketId(id, TicketFieldConfiguration.Context.ATTENDEE.name());
        } else {
            values = findAllByTicketId(id);
        }
        return values.stream()
            .collect(Collectors.toMap(TicketFieldValue::getName, Function.identity()));
    }

    default boolean hasOptionalData(int ticketId) {
        return countFilledOptionalData(ticketId) > 0;
    }


    @Query("select * from purchase_context_field_configuration where event_id_fk = :eventId order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventId") int eventId);

    @Query("select * from purchase_context_field_configuration where event_id_fk = :eventId" +
        " and field_type = :type order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsOfTypeForEvent(@Bind("eventId") int eventId, @Bind("type") String type);
    
    @Query("select * from purchase_context_field_configuration where id = :id")
    TicketFieldConfiguration findById(@Bind("id") long id);
    
    @Query("update purchase_context_field_configuration set field_order = :order where id = :id")
    int updateFieldOrder(@Bind("id") long id, @Bind("order") int order);

    @Query("select purchase_context_field_configuration.* from purchase_context_field_configuration" +
        " inner join event on event.id = event_id_fk" +
        " where short_name = :eventShortName order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventShortName") String eventName);

    @Query("select count(*) from purchase_context_field_configuration where event_id_fk = :eventId")
    Integer countAdditionalFieldsForEvent(@Bind("eventId") int eventId);
    
    @Query("select max(field_order) from purchase_context_field_configuration where event_id_fk = :eventId")
    Integer findMaxOrderValue(@Bind("eventId") int eventId);

    default Map<Long, TicketFieldDescription> findTranslationsFor(Locale locale, int eventId) {
        return findDescriptions(eventId, locale.getLanguage()).stream().collect(Collectors.toMap(TicketFieldDescription::getFieldConfigurationId, Function.identity()));
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

    @Query("select field_value as name, count(*) as count from ticket_field_value_w_additional where field_configuration_id_fk = :configurationId group by field_value")
    List<RestrictedValueStats.RestrictedValueCount> getValueStats(@Bind("configurationId") long configurationId);

    default List<RestrictedValueStats> retrieveStats(long configurationId) {
        TicketFieldConfiguration configuration = findById(configurationId);
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
}
