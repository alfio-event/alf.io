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
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@QueryRepository
public interface TicketFieldRepository extends FieldRepository {

    @Query("select count(*) from ticket_field_value where ticket_id_fk = :ticketId and field_value is not null and field_value <> ''")
    Integer countFilledOptionalData(@Bind("ticketId") int id);

    @Query("select a.ticket_id_fk, a.ticket_field_configuration_id_fk, b.field_name, a.field_value from ticket_field_value a, ticket_field_configuration b where a.ticket_id_fk = :ticketId and a.ticket_field_configuration_id_fk = b.id")
    List<TicketFieldValue> findAllByTicketId(@Bind("ticketId") int id);

    @Query("select a.ticket_id_fk, a.ticket_field_configuration_id_fk, b.field_name, a.field_value, null as description " +
        "from ticket_field_value a inner join ticket_field_configuration b on a.ticket_field_configuration_id_fk = b.id " +
        "where a.ticket_id_fk = :ticketId and b.field_name in (:fieldNames) and b.field_type <> 'select' " +
        "union all " +
        "select a.ticket_id_fk, a.ticket_field_configuration_id_fk, b.field_name, a.field_value, c.description " +
        "from ticket_field_value a " +
        "inner join ticket_field_configuration b on a.ticket_field_configuration_id_fk = b.id " +
        "inner join ticket on a.ticket_id_fk = ticket.id "+
        "left join ticket_field_description c on c.ticket_field_configuration_id_fk = a.ticket_field_configuration_id_fk " +
        "where a.ticket_id_fk = :ticketId and b.field_name in (:fieldNames) " +
        "and c.field_locale = ticket.user_language " +
        "and b.field_type = 'select'")
    List<TicketFieldValueAndDescription> findValueForTicketId(@Bind("ticketId") int id, @Bind("fieldNames") Set<String> fieldNames);

    @Query("update ticket_field_value set field_value = :value where ticket_id_fk = :ticketId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int updateValue(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("insert into ticket_field_value(ticket_id_fk, ticket_field_configuration_id_fk, field_value) values (:ticketId, :fieldConfigurationId, :value)")
    int insertValue(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("delete from ticket_field_value where ticket_id_fk = :ticketId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int deleteValue(@Bind("ticketId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId);

    @Query("delete from ticket_field_value where ticket_id_fk = :ticketId")
    int deleteAllValuesForTicket(@Bind("ticketId") int ticketId);

    @Query("delete from ticket_field_value where ticket_id_fk in (:ticketIds)")
    int deleteAllValuesForTicketIds(@Bind("ticketIds") List<Integer> ticketIds);

    @Query("delete from ticket_field_value fv using ticket t where t.id = fv.ticket_id_fk and t.tickets_reservation_id in(:reservationIds)")
    int deleteAllValuesForReservations(@Bind("reservationIds") List<String> reservationIds);

    @Query("select ticket_field_configuration_id_fk, field_locale, description from ticket_field_description  inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where field_locale = :locale and event_id_fk = :eventId")
    List<TicketFieldDescription> findDescriptions(@Bind("eventId") int eventId, @Bind("locale") String locale);

    @Query("select ticket_field_description.* from ticket_field_description  inner join ticket_field_configuration on ticket_field_configuration_id_fk = id inner join event on event.id = event_id_fk  where short_name = :eventShortName")
    List<TicketFieldDescription> findDescriptions(@Bind("eventShortName") String eventShortName);

    @Query("SELECT field_name FROM ticket_field_configuration inner join event on event.id = event_id_fk  where short_name = :eventShortName order by field_order asc ")
    List<String> findFieldsForEvent(@Bind("eventShortName") String eventShortName);

    @Query("select field_name, field_value from ticket_field_value inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where ticket_id_fk = :ticketId")
    List<FieldNameAndValue> findNameAndValue(@Bind("ticketId") int ticketId);

    @Query("select ticket_id_fk, ticket_field_configuration_id_fk, field_name, field_value from ticket_field_value inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where ticket_id_fk in (:ticketIds)")
    List<TicketFieldValue> findAllValuesByTicketIds(@Bind("ticketIds") Collection<Integer> ticketIds);

    default void updateOrInsert(Map<String, List<String>> values, int ticketId, int eventId) {
        Map<String, TicketFieldValue> toUpdate = findAllByTicketIdGroupedByName(ticketId);
        values = Optional.ofNullable(values).orElseGet(Collections::emptyMap);
        Map<String, Integer> fieldNameToId = findAdditionalFieldsForEvent(eventId).stream().collect(Collectors.toMap(TicketFieldConfiguration::getName, TicketFieldConfiguration::getId));

        values.forEach((fieldName, fieldValues) -> {
            String fieldValue;
            if(fieldValues.size() == 1) {
                fieldValue = fieldValues.get(0);
            } else if(fieldValues.stream().anyMatch(StringUtils::isNotBlank)) {
                fieldValue = Json.toJson(fieldValues);
            } else {
                fieldValue = "";
            }

            boolean isNotBlank = StringUtils.isNotBlank(fieldValue);
            if(toUpdate.containsKey(fieldName)) {
                TicketFieldValue field = toUpdate.get(fieldName);
                if(isNotBlank) {
                    updateValue(field.getTicketId(), field.getTicketFieldConfigurationId(), fieldValue);
                } else {
                    deleteValue(field.getTicketId(), field.getTicketFieldConfigurationId());
                }
            } else if(fieldNameToId.containsKey(fieldName) && isNotBlank) {
                insertValue(ticketId, fieldNameToId.get(fieldName), fieldValue);
            }
        });
    }

    default Map<String, TicketFieldValue> findAllByTicketIdGroupedByName(int id) {
        return findAllByTicketId(id).stream().collect(Collectors.toMap(TicketFieldValue::getName, Function.identity()));
    }

    default boolean hasOptionalData(int ticketId) {
        return countFilledOptionalData(ticketId) > 0;
    }


    @Query("select * from ticket_field_configuration where event_id_fk = :eventId order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventId") int eventId);
    
    @Query("select * from ticket_field_configuration where id = :id")
    TicketFieldConfiguration findById(@Bind("id") int id);
    
    @Query("update ticket_field_configuration set field_order = :order where id = :id")
    int updateFieldOrder(@Bind("id") int id, @Bind("order") int order);

    @Query("select ticket_field_configuration.* from ticket_field_configuration inner join event on event.id = event_id_fk  where short_name = :eventShortName order by field_order asc")
    List<TicketFieldConfiguration> findAdditionalFieldsForEvent(@Bind("eventShortName") String eventName);

    @Query("select count(*) from ticket_field_configuration where event_id_fk = :eventId")
    Integer countAdditionalFieldsForEvent(@Bind("eventId") int eventId);
    
    @Query("select max(field_order) from ticket_field_configuration where event_id_fk = :eventId")
    Integer findMaxOrderValue(@Bind("eventId") int eventId);

    @Query("select count(*) from ticket_field_configuration where event_id_fk = :eventId and field_required = true")
    Integer countRequiredAdditionalFieldsForEvent(@Bind("eventId") int eventId);

    default Map<Integer, TicketFieldDescription> findTranslationsFor(Locale locale, int eventId) {
        return findDescriptions(eventId, locale.getLanguage()).stream().collect(Collectors.toMap(TicketFieldDescription::getTicketFieldConfigurationId, Function.identity()));
    }

    default Map<String, String> findAllValuesForTicketId(int ticketId) {
        return findNameAndValue(ticketId).stream().filter(t -> t.getName() != null && t.getValue() != null).collect(Collectors.toMap(FieldNameAndValue::getName, FieldNameAndValue::getValue));
    }

    // required for deleting a field
    
    @Query("delete from ticket_field_value where ticket_field_configuration_id_fk = :fieldConfigurationId")
	int deleteValues(@Bind("fieldConfigurationId") int ticketFieldConfigurationId);
    
    @Query("delete from ticket_field_description where ticket_field_configuration_id_fk = :fieldConfigurationId")
	int deleteDescription(@Bind("fieldConfigurationId") int ticketFieldConfigurationId);

    @Query("delete from ticket_field_configuration where id = :fieldConfigurationId")
	int deleteField(@Bind("fieldConfigurationId") int ticketFieldConfigurationId);

    @Query("select field_value as name, count(*) as count from ticket_field_value where ticket_field_configuration_id_fk = :configurationId group by field_value")
    List<RestrictedValueStats.RestrictedValueCount> getValueStats(@Bind("configurationId") int configurationId);

    default List<RestrictedValueStats> retrieveStats(int configurationId) {
        TicketFieldConfiguration configuration = findById(configurationId);
        Map<String, Integer> valueStats = getValueStats(configurationId).stream().collect(Collectors.toMap(RestrictedValueStats.RestrictedValueCount::getName, RestrictedValueStats.RestrictedValueCount::getCount));
        int total = valueStats.values().stream().mapToInt(i -> i).sum();
        return configuration.getRestrictedValues().stream()
            .map(name -> {
                int count = valueStats.getOrDefault(name, 0);
                return new RestrictedValueStats(name, count, new BigDecimal(count).divide(new BigDecimal(total), 2, RoundingMode.HALF_UP).multiply(MonetaryUtil.HUNDRED).intValue());
            }).collect(Collectors.toList());

    }

    @Query("select c2.field_name as field_name, tfv.field_value as field_value, c2.additional_service_id as additional_service_id from ticket_field_value tfv" +
        "  join ticket_field_configuration c2 on tfv.ticket_field_configuration_id_fk = c2.id" +
        "  where tfv.ticket_id_fk = :ticketId" +
        "  and c2.context = 'ADDITIONAL_SERVICE'" +
        "  and c2.additional_service_id in (:additionalServiceIds)")
    List<TicketFieldValueForAdditionalService> loadTicketFieldsForAdditionalService(@Bind("ticketId") int ticketId,
                                                                                    @Bind("additionalServiceIds") List<Integer> additionalServiceIds);
}
