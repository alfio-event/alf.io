package alfio.repository;

import alfio.model.AdditionalServiceFieldValue;
import alfio.model.FieldNameAndValue;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface AdditionalServiceFieldValueRepository {

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
