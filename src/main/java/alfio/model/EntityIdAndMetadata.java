package alfio.model;

import alfio.model.metadata.AlfioMetadata;
import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class EntityIdAndMetadata {
    private final Integer id;
    private final AlfioMetadata metadata;

    public EntityIdAndMetadata(@Column("id") Integer id,
                               @Column("metadata") @JSONData AlfioMetadata metadata) {
        this.id = id;
        this.metadata = metadata;
    }
}
