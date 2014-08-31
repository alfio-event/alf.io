package io.bagarino.repository.join;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.join.EventOrganization;

import java.util.List;

@QueryRepository
public interface EventOrganizationRepository {

    @Query("select * from j_event_organization where event_id = :eventId")
    EventOrganization getByEventId(@Bind("eventId") int eventId);

    @Query("select * from j_event_organization where org_id = :organizationId")
    List<EventOrganization> findByOrganizationId(@Bind("organizationId") int organizationId);

}
