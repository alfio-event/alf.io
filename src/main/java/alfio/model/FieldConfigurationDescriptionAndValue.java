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
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
public class FieldConfigurationDescriptionAndValue {

    @Delegate
    private final PurchaseContextFieldConfiguration purchaseContextFieldConfiguration;
    @Delegate
    private final PurchaseContextFieldDescription purchaseContextFieldDescription;
    private final int count;
    private final String value;

    private static final List<String> TEXT_FIELD_TYPES = List.of(
        "text",
        "tel",
        "textarea",
        "vat:eu",
        "dateOfBirth"
    );
    private static final Pattern CHECKBOX_VALUES_PATTERN = Pattern.compile("\"(.*?)\",?");

    public String getTranslatedValue() {
        if(StringUtils.isBlank(value)) {
            return value;
        }
        if(isSelectField()) {
            return purchaseContextFieldDescription.getRestrictedValuesDescription().getOrDefault(value, "MISSING_DESCRIPTION");
        }
        return value;
    }

    public List<Triple<String, String, Boolean>> getTranslatedRestrictedValue() {
        Map<String, String> description = purchaseContextFieldDescription.getRestrictedValuesDescription();
        return purchaseContextFieldConfiguration.getRestrictedValues()
            .stream()
            .map(val -> Triple.of(val, description.getOrDefault(val, "MISSING_DESCRIPTION"), isFieldValueEnabled(purchaseContextFieldConfiguration, val)))
            .collect(Collectors.toList());
    }

    public List<TicketFieldValue> getFields() {
        if(count == 1) {
            return Collections.singletonList(new TicketFieldValue(0, 1, value, isAcceptingValues()));
        }
        List<String> values = StringUtils.isBlank(value) ? Collections.emptyList() : Json.fromJson(value, new TypeReference<>() {});
        return IntStream.range(0, count)
            .mapToObj(i -> new TicketFieldValue(i, i+1, i < values.size() ? values.get(i) : "", isAcceptingValues()))
            .collect(Collectors.toList());

    }

    private boolean isText() {
        return purchaseContextFieldConfiguration.isTextField()
            || TEXT_FIELD_TYPES.contains(purchaseContextFieldConfiguration.getType());
    }

    public String getValueDescription() {
        if(isText()) {
            return value;
        } else if(isCheckboxField()) {
            var matches = new ArrayList<String>();
            var matcher = CHECKBOX_VALUES_PATTERN.matcher(value);
            while(matcher.find()) {
                matches.add(matcher.group(1));
            }
            var restrictedValues = getTranslatedRestrictedValue();
            return matches.stream()
                .map(v -> findValueDescription(restrictedValues, v))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
        } else {
            return findValueDescription(getTranslatedRestrictedValue(), value);
        }
    }

    private String findValueDescription(List<Triple<String, String, Boolean>> translateRestrictedValues,
                                        String value) {
        return translateRestrictedValues.stream()
            .filter(t -> StringUtils.equals(t.getLeft(), value))
            .map(Triple::getMiddle)
            .findFirst()
            .orElse("");
    }

    public String getValue() {
        return value;
    }

    private boolean isAcceptingValues() {
        return isEditable() || StringUtils.isBlank(value);
    }

    public boolean isBeforeStandardFields() {
        return isBeforeStandardFields(purchaseContextFieldConfiguration);
    }

    private static boolean isFieldValueEnabled(PurchaseContextFieldConfiguration purchaseContextFieldConfiguration, String value) {
        return !purchaseContextFieldConfiguration.isSelectField()
            || CollectionUtils.isEmpty(purchaseContextFieldConfiguration.getDisabledValues())
            || !purchaseContextFieldConfiguration.getDisabledValues().contains(value);
    }

    @RequiredArgsConstructor
    @Getter
    public static class TicketFieldValue {
        private final int fieldIndex;
        private final int fieldCounter;
        private final String fieldValue;
        private final Boolean editable;
    }

    public static boolean isBeforeStandardFields(PurchaseContextFieldConfiguration purchaseContextFieldConfiguration) {
        return purchaseContextFieldConfiguration.getOrder() < 0;
    }

}
