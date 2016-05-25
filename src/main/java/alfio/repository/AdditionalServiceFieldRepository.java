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

import alfio.model.AdditionalServiceFieldValue;
import alfio.model.FieldNameAndValue;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface AdditionalServiceFieldRepository extends FieldRepository {

    @Query("select a.additional_service_id_fk, a.ticket_field_configuration_id_fk, b.field_name, a.field_value from additional_service_field_value a, ticket_field_configuration b where a.additional_service_id_fk = :additionalServiceId and a.ticket_field_configuration_id_fk = b.id and b.context = 'ADDITIONAL_SERVICE'")
    List<AdditionalServiceFieldValue> findAllByAdditionalServiceId(@Bind("additionalServiceId") int id);

    @Query("update additional_service_field_value set field_value = :value where additional_service_id_fk = :additionalServiceId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int updateValue(@Bind("additionalServiceId") int additionalServiceId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("insert into additional_service_field_value(additional_service_id_fk, ticket_field_configuration_id_fk, field_value) values (:ticketId, :fieldConfigurationId, :value)")
    int insertValue(@Bind("additionalServiceId") int additionalServiceId, @Bind("fieldConfigurationId") int fieldConfigurationId, @Bind("value") String value);

    @Query("delete from additional_service_field_value where additional_service_id_fk = :additionalServiceId and ticket_field_configuration_id_fk = :fieldConfigurationId")
    int deleteValue(@Bind("additionalServiceId") int ticketId, @Bind("fieldConfigurationId") int fieldConfigurationId);

    @Query("delete from additional_service_field_value where ticket_field_configuration_id_fk = :fieldConfigurationId")
    int deleteValues(@Bind("fieldConfigurationId") int ticketFieldConfigurationId);

    @Query("select field_name, field_value from additional_service_field_value inner join ticket_field_configuration on ticket_field_configuration_id_fk = id where additional_service_id_fk = :additionalServiceId and context = 'ADDITIONAL_SERVICE'")
    List<FieldNameAndValue> findNameAndValue(@Bind("additionalServiceId") int additionalServiceId);

}
