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

import lombok.Getter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Getter
public class UpdateSubscriptionOwnerForm implements Serializable, AdditionalFieldsContainer {
    private String email;
    private String firstName;
    private String lastName;
    private Map<String, List<String>> additional;

    public void setEmail(String email) {
        this.email = trimToNull(email);
    }

    public void setFirstName(String firstName) {
        this.firstName = trimToNull(firstName);
    }

    public void setLastName(String lastName) {
        this.lastName = trimToNull(lastName);
    }

    public Map<String, List<String>> getAdditional() {
        return additional;
    }

    public void setAdditional(Map<String, List<String>> additional) {
        this.additional = additional;
    }

    public boolean validate() {
        return this.email != null && this.firstName != null && this.lastName != null;
    }
}
