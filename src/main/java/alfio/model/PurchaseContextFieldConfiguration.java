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
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.regex.Pattern;

@Getter
public class PurchaseContextFieldConfiguration {

    public static final Set<Context> EVENT_RELATED_CONTEXTS = Set.copyOf(EnumSet.of(Context.ATTENDEE, Context.ADDITIONAL_SERVICE));
    private static final Pattern COLON_SPLITTER = Pattern.compile(":");

    public enum Context {
        ATTENDEE, ADDITIONAL_SERVICE, SUBSCRIPTION
    }

    private final long id;
    private final Integer eventId;
    private final UUID subscriptionDescriptorId;
    private final String name;
    private final int order;
    private final String type;
    private final Integer maxLength;
    private final Integer minLength;
    private final boolean required;
    private final boolean editable;
    private final List<String> restrictedValues;
    private final Context context;
    private final Integer additionalServiceId;
    private final List<Integer> categoryIds;
    private final List<String> disabledValues;


    public PurchaseContextFieldConfiguration(@Column("id") int id,
                                             @Column("event_id_fk") Integer eventId,
                                             @Column("subscription_descriptor_id_fk") UUID subscriptionDescriptorId,
                                             @Column("field_name") String name,
                                             @Column("field_order") int order,
                                             @Column("field_type") String type,
                                             @Column("field_maxlength") Integer maxLength,
                                             @Column("field_minlength") Integer minLength,
                                             @Column("field_required") boolean required,
                                             @Column("field_editable") boolean editable,
                                             @Column("field_restricted_values") String restrictedValues,
                                             @Column("context") Context context,
                                             @Column("additional_service_id") Integer additionalServiceId,
                                             @Column("ticket_category_ids") String ticketCategoryIds,
                                             @Column("field_disabled_values") String disabledValues) {
        this.id = id;
        this.eventId = eventId;
        this.subscriptionDescriptorId = subscriptionDescriptorId;
        this.name = name;
        this.order = order;
        this.type = type;
        this.maxLength = maxLength;
        this.minLength = minLength;
        this.required = required;
        this.editable = editable;
        this.restrictedValues = restrictedValues == null ? Collections.emptyList() : Json.GSON.fromJson(restrictedValues, new TypeToken<List<String>>(){}.getType());
        this.disabledValues = disabledValues == null ? Collections.emptyList() : Json.GSON.fromJson(disabledValues, new TypeToken<List<String>>(){}.getType());
        this.context = context;
        this.additionalServiceId = additionalServiceId;
        this.categoryIds = ticketCategoryIds == null ? Collections.emptyList() : Json.GSON.fromJson(ticketCategoryIds, new TypeToken<List<Integer>>(){}.getType());
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

    public boolean isCheckboxField() {
        return "checkbox".equals(type);
    }

    public int getCount() {
        if ("checkbox".equals(type) && this.restrictedValues != null) {
            return Math.max(this.restrictedValues.size(), 1);
        } else {
            return 1;
        }
    }

    public boolean isEuVat() {
        return "vat:eu".equals(type);
    }

    public boolean isDateOfBirth() {
        return "input:dateOfBirth".equals(type);
    }

    public String getInputType() {
        String[] split = COLON_SPLITTER.split(type);
        return split.length == 2 ? split[1] : "text";
    }

    public boolean isMaxLengthDefined() {
        return maxLength != null;
    }

    public boolean hasDisabledValues() {
        return CollectionUtils.isNotEmpty(disabledValues);
    }

    public boolean isMinLengthDefined() {
        return minLength != null;
    }

    public boolean rulesApply(Integer ticketCategoryId) {
        return categoryIds.isEmpty() || categoryIds.contains(ticketCategoryId);
    }

    public boolean isReadOnly() {
        return !editable;
    }

    public boolean isTextField() {
        return isInputField() || isTextareaField();
    }
}
