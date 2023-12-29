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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public class PurchaseContextFieldDescription {

    public static final PurchaseContextFieldDescription MISSING_FIELD = new PurchaseContextFieldDescription(-1, "", "{\"label\" : \"MISSING_DESCRIPTION\"}", "");

    private final long fieldConfigurationId;
    private final String locale;
    private final Map<String, Object> description;
    private final String fieldName;

    public PurchaseContextFieldDescription(@Column("field_configuration_id_fk") long fieldConfigurationId,
                                           @Column("field_locale") String locale,
                                           @Column("description") String description,
                                           @Column("field_name") String fieldName) {
        this.locale = locale;
        this.fieldConfigurationId = fieldConfigurationId;
        this.description = Json.GSON.fromJson(description, new TypeToken<Map<String, Object>>(){}.getType());
        this.fieldName = fieldName;
    }

    @JsonIgnore
    public String getLabelDescription() {
        return description.get("label").toString();
    }

    @JsonIgnore
    public boolean isPlaceholderDescriptionDefined() {
        return description.containsKey("placeholder");
    }

    @JsonIgnore
    public String getPlaceholderDescription() {
        return isPlaceholderDescriptionDefined() ? description.get("placeholder").toString() : null;
    }

    @JsonIgnore
    @SuppressWarnings("unchecked")
    public Map<String, String> getRestrictedValuesDescription() {
        return (Map<String, String>) description.getOrDefault("restrictedValues", Collections.emptyMap());
    }
}
