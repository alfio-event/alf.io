package io.bagarino.model.join;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class EventOrganization {
    private final int eventId;
    private final int organizationId;

    public EventOrganization(@Column("event_id") int eventId,
                             @Column("org_id") int organizationId) {
        this.eventId = eventId;
        this.organizationId = organizationId;
    }
}
