package alfio.model;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class BookedAdditionalService {

    private final String additionalServiceName;
    private final int additionalServiceId;
    private final int count;

    public BookedAdditionalService(@Column("as_name") String additionalServiceName,
                                   @Column("as_id") int additionalServiceId,
                                   @Column("qty") int count) {
        this.additionalServiceName = additionalServiceName;
        this.additionalServiceId = additionalServiceId;
        this.count = count;
    }
}
