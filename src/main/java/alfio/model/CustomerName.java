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

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

@Getter
public class CustomerName {
    private final String fullName;
    private final String firstName;
    private final String lastName;

    private final boolean hasFirstAndLastName;

    public CustomerName(String fullName, String firstName, String lastName, Event event) {
        this(fullName, firstName, lastName, event, true);
    }

    public CustomerName(String fullName, String firstName, String lastName, Event event, boolean validate) {
        this.firstName = StringUtils.trimToNull(firstName);
        this.lastName = StringUtils.trimToNull(lastName);
        hasFirstAndLastName = event.mustUseFirstAndLastName();
        fullName = StringUtils.trimToNull(fullName);
        if(hasFirstAndLastName) {
            if(validate) {
                Validate.isTrue(this.firstName != null, "firstName must not be null");
                Validate.isTrue(this.lastName != null, "lastName must not be null");
            }

            this.fullName = firstName + " " + lastName;
        } else {
            if (validate) {
                Validate.isTrue(fullName != null, "fullName must not be null");
            }
            this.fullName = fullName;
        }
    }

    @Override
    public String toString() {
        if(hasFirstAndLastName) {
            return firstName+ " " + lastName;
        } else {
            return fullName;
        }
    }
}
