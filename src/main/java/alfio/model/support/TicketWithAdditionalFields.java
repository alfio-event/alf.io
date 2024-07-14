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
package alfio.model.support;

import alfio.model.FieldConfigurationDescriptionAndValue;
import alfio.model.Ticket;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TicketWithAdditionalFields {
    private final Ticket ticket;
    private final List<FieldConfigurationDescriptionAndValue> additionalFieldsDescriptionsAndValues;

    public TicketWithAdditionalFields(Ticket ticket,
                                      List<FieldConfigurationDescriptionAndValue> additionalFieldsDescriptionsAndValues) {
        this.ticket = ticket;
        this.additionalFieldsDescriptionsAndValues = additionalFieldsDescriptionsAndValues;
    }

    public Map<String, String> getAdditionalFields() {
        return additionalFieldsDescriptionsAndValues.stream()
            .map(af -> Pair.of(af.getLabelDescription(), af.getValueDescription()))
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public String getUuid() {
        return ticket.getUuid();
    }

    public String getFullName() {
        return ticket.getFullName();
    }

    public String getFirstName() {
        return ticket.getFirstName();
    }

    public String getLastName() {
        return ticket.getLastName();
    }

    public String getEmail() {
        return ticket.getEmail();
    }

    public String getUserLanguage() {
        return ticket.getUserLanguage();
    }
}
