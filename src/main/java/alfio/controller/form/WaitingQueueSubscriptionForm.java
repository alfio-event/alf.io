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
package alfio.controller.form;

import alfio.model.CustomerName;
import alfio.model.Event;
import lombok.Data;

import java.io.Serializable;
import java.util.Locale;

@Data
public class WaitingQueueSubscriptionForm implements Serializable {
    private String fullName;
    private String firstName;
    private String lastName;
    private String email;
    private Locale userLanguage;
    private boolean termAndConditionsAccepted;
    private boolean privacyPolicyAccepted;
    private Integer selectedCategory;

    public CustomerName toCustomerName(Event event) {
        return new CustomerName(fullName, firstName, lastName, event.mustUseFirstAndLastName());
    }
}
