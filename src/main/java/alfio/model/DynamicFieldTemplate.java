/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.model;

import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        this.restrictedValues = Optional.ofNullable(restrictedValuesJson).map(DynamicFieldTemplate::parseRestrictedValues).orElse(Collections.emptyList());
        this.description = Json.GSON.fromJson(descriptionJson, new TypeToken<Map<String, Object>>(){}.getType());
        this.maxLength = maxLength;
        this.minLength = minLength;
    }

    private static List<String> parseRestrictedValues(String v) {
        return Json.GSON.fromJson(v, new TypeToken<List<String>>(){}.getType());
    }
}
