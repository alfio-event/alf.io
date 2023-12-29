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
package alfio.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class AdditionalFieldRequest implements EventModification.WithRestrictedValues, EventModification.WithLinkedCategories {
    private final int order;
    private final boolean useDefinedOrder;
    private final String name;
    private final String type;
    private final boolean required;
    private final boolean readOnly;

    private final Integer minLength;
    private final Integer maxLength;
    private final List<EventModification.RestrictedValue> restrictedValues;

    // locale -> description
    private final Map<String, EventModification.Description> description;
    private final EventModification.AdditionalService linkedAdditionalService;
    private final List<Integer> linkedCategoryIds;


    @JsonCreator
    public AdditionalFieldRequest(@JsonProperty("order") int order,
                                  @JsonProperty("useDefinedOrder") Boolean useDefinedOrder,
                                  @JsonProperty("name") String name,
                                  @JsonProperty("type") String type,
                                  @JsonProperty("required") boolean required,
                                  @JsonProperty("readOnly") boolean readOnly,
                                  @JsonProperty("minLength") Integer minLength,
                                  @JsonProperty("maxLength") Integer maxLength,
                                  @JsonProperty("restrictedValues") List<EventModification.RestrictedValue> restrictedValues,
                                  @JsonProperty("description") Map<String, EventModification.Description> description,
                                  @JsonProperty("forAdditionalService") EventModification.AdditionalService linkedAdditionalService,
                                  @JsonProperty("categoryIds") List<Integer> linkedCategoryIds) {
        this.order = order;
        this.useDefinedOrder = Boolean.TRUE.equals(useDefinedOrder);
        this.name = name;
        this.type = type;
        this.required = required;
        this.readOnly = readOnly;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.restrictedValues = restrictedValues;
        this.description = description;
        this.linkedAdditionalService = linkedAdditionalService;
        this.linkedCategoryIds = linkedCategoryIds;
    }

    @Override
    public List<String> getRestrictedValuesAsString() {
        return restrictedValues == null ? Collections.emptyList() : restrictedValues.stream().map(EventModification.RestrictedValue::getValue).collect(Collectors.toList());
    }

    @Override
    public List<String> getDisabledValuesAsString() {
        return Collections.emptyList();
    }

    @Override
    public List<Integer> getLinkedCategoriesIds() {
        return linkedCategoryIds == null ? Collections.emptyList() : linkedCategoryIds;
    }
}
