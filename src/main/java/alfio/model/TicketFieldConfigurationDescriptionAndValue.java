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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@AllArgsConstructor
public class TicketFieldConfigurationDescriptionAndValue {

    @Delegate
    private final TicketFieldConfiguration ticketFieldConfiguration;
    @Delegate
    private final TicketFieldDescription ticketFieldDescription;
    private final int count;
    private final String value;


    public List<Triple<String, String, Boolean>> getTranslatedRestrictedValue() {
        Map<String, String> description = ticketFieldDescription.getRestrictedValuesDescription();
        return ticketFieldConfiguration.getRestrictedValues()
            .stream()
            .map(val -> Triple.of(val, description.getOrDefault(val, "MISSING_DESCRIPTION"), isFieldValueEnabled(ticketFieldConfiguration, val)))
            .collect(Collectors.toList());
    }

    public List<TicketFieldValue> getFields() {
        if(count == 1) {
            return Collections.singletonList(new TicketFieldValue(0, 1, value));
        }
        List<String> values = StringUtils.isBlank(value) ? Collections.emptyList() : Json.fromJson(value, new TypeReference<List<String>>() {});
        return IntStream.range(0, count)
            .mapToObj(i -> new TicketFieldValue(i, i+1, i < values.size() ? values.get(i) : ""))
            .collect(Collectors.toList());

    }

    public String getValueDescription() {
        if(isSelectField()) {
            return getTranslatedRestrictedValue().stream()
                .filter(t -> StringUtils.equals(t.getLeft(), value))
                .map(Triple::getMiddle)
                .findFirst()
                .orElse("");
        } else {
            return value;
        }
    }

    public String getValue() {
        return value;
    }

    public boolean isBeforeStandardFields() {
        return getOrder() < 0;
    }

    private static boolean isFieldValueEnabled(TicketFieldConfiguration ticketFieldConfiguration, String value) {
        return !ticketFieldConfiguration.isSelectField()
            || CollectionUtils.isEmpty(ticketFieldConfiguration.getDisabledValues())
            || !ticketFieldConfiguration.getDisabledValues().contains(value);
    }

    @RequiredArgsConstructor
    @Getter
    public static class TicketFieldValue {
        private final int fieldIndex;
        private final int fieldCounter;
        private final String fieldValue;
    }

}
