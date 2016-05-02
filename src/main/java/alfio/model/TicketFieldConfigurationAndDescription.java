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

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class TicketFieldConfigurationAndDescription {

    @Delegate
    private final TicketFieldConfiguration ticketFieldConfiguration;
    @Delegate
    private final TicketFieldDescription ticketFieldDescription;


    public List<Pair<String, String>> getTranslatedRestrictedValue() {
        Map<String, String> description = ticketFieldDescription.getRestrictedValuesDescription();
        return ticketFieldConfiguration.getRestrictedValues()
            .stream()
            .map(val -> Pair.of(val, description.getOrDefault(val, "MISSING_DESCRIPTION")))
            .collect(Collectors.toList());
    }
}
