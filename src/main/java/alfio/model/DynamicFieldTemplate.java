package alfio.model;

import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Getter
public class DynamicFieldTemplate {

    private final int id;
    private final String name;
    private final String type;
    private final List<String> restrictedValues;
    private final Map<String, Object> description;
    private final Integer maxLength;
    private final Integer minLength;

    public DynamicFieldTemplate(@Column("id") int id,
                                @Column("field_name") String name,
                                @Column("field_type") String type,
                                @Column("field_restricted_values") String restrictedValuesJson,
                                @Column("field_description") String descriptionJson,
                                @Column("field_maxlength") Integer maxLength,
                                @Column("field_minlength") Integer minLength) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.restrictedValues = Optional.ofNullable(restrictedValuesJson).map(parseRestrictedValues()).orElse(Collections.emptyList());
        this.description = Json.GSON.fromJson(descriptionJson, new TypeToken<Map<String, Object>>(){}.getType());
        this.maxLength = maxLength;
        this.minLength = minLength;
    }

    private static Function<String, List<String>> parseRestrictedValues() {
        return v -> Json.GSON.fromJson(v, new TypeToken<List<String>>(){}.getType());
    }
}
