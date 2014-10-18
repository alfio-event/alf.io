package io.bagarino.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ConfigurationModification {
    private final Integer id;
    private final String key;
    private final String value;

    @JsonCreator
    public ConfigurationModification(@JsonProperty("id") Integer id,
                                     @JsonProperty("key") String key,
                                     @JsonProperty("value") String value) {
        this.id = id;
        this.key = key;
        this.value = value;
    }
}
