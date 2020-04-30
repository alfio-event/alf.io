package alfio.model.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AlfioAttribute {
    private final String key;
    private final Object value;
    @JsonCreator
    public AlfioAttribute(@JsonProperty("key") String key, @JsonProperty("value") Object value) {
        this.key = key;
        this.value = value;
    }
}
