package alfio.repository;

import alfio.model.AdditionalServiceDescription;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface AdditionalServiceDescriptionRepository {

    @Query("select additional_service_id_fk, locale, type, value from additional_service_description where additional_service_id_fk = :additionalServiceId")
    List<AdditionalServiceDescription> findAllByAdditionalServiceId(@Bind("additionalServiceId") int additionalServiceId);


}
