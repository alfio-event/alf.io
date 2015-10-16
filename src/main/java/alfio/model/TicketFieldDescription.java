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
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
public class TicketFieldDescription {

    public static final TicketFieldDescription MISSING_FIELD = new TicketFieldDescription(-1, "{\"label\" : \"MISSING_DESCRIPTION\"}");

    private final int ticketFieldConfigurationId;
    private final Map<String, Object> description;

    public TicketFieldDescription(@Column("ticket_field_configuration_id_fk") int ticketFieldConfigurationId,
                                  @Column("description") String description) {
        this.ticketFieldConfigurationId = ticketFieldConfigurationId;
        this.description = Json.GSON.fromJson(description, Map.class);
    }


    public String getLabelDescription() {
        return description.get("label").toString();
    }

    public boolean isPlaceholderDescriptionDefined() {
        return description.containsKey("placeholder");
    }

    public String getPlaceholderDescription() {
        return description.get("placeholder").toString();
    }

    public Map<String, String> getRestrictedValuesDescription() {
        return (Map<String, String>) description.getOrDefault("restrictedValues", Collections.emptyMap());
    }
}
