package alfio.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class CategoryOrdinalModification {
    private final int id;
    private final String name;
    private final int ordinal;

    @JsonCreator
    public CategoryOrdinalModification(@JsonProperty("id") int id, @JsonProperty("name") String name, @JsonProperty("ordinal") int ordinal) {
        this.id = id;
        this.name = name;
        this.ordinal = ordinal;
    }
}
