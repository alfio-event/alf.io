package alfio.repository;

import alfio.model.DynamicFieldTemplate;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryRepository;

import java.util.List;

@QueryRepository
public interface DynamicFieldTemplateRepository {

    @Query("select * from dynamic_field_template")
    List<DynamicFieldTemplate> loadAll();

}
