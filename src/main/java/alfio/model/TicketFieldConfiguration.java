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

@Getter
public class TicketFieldConfiguration {

    public enum Context {
        ATTENDEE, ADDITIONAL_SERVICE
    }

    private final int id;
    private final int eventId;
    private final String name;
    private final int order;
    private final String type;
    private final Integer maxLength;
    private final Integer minLength;
    private final boolean required;
    private final List<String> restrictedValues;
    private final Context context;
    private Integer additionalServiceId;


    public TicketFieldConfiguration(@Column("id") int id,
                                    @Column("event_id_fk") int eventId,
                                    @Column("field_name") String name,
                                    @Column("field_order") int order,
                                    @Column("field_type") String type,
                                    @Column("field_maxlength") Integer maxLength,
                                    @Column("field_minlength") Integer minLength,
                                    @Column("field_required") boolean required,
                                    @Column("field_restricted_values") String restrictedValues,
                                    @Column("context") Context context,
                                    @Column("additional_service_id") Integer additionalServiceId) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.order = order;
        this.type = type;
        this.maxLength = maxLength;
        this.minLength = minLength;
        this.required = required;
        this.restrictedValues = restrictedValues == null ? Collections.emptyList() : Json.GSON.fromJson(restrictedValues, new TypeToken<List<String>>(){}.getType());
        this.context = context;
        this.additionalServiceId = additionalServiceId;
    }

    public boolean isInputField() {
        return type.startsWith("input:");
    }

    public boolean isTextareaField() {
        return "textarea".equals(type);
    }

    public boolean isCountryField() {
        return "country".equals(type);
    }

    public boolean isSelectField() {
        return "select".equals(type);
    }

    public String getInputType() {
        String[] splitted = type.split(":");
        return splitted.length == 2 ? splitted[1] : "text";
    }

    public boolean isMaxLengthDefined() {
        return maxLength != null;
    }

    public boolean isMinLengthDefined() {
        return minLength != null;
    }
}
