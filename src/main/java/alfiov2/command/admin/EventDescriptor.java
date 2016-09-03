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
package alfiov2.command.admin;

import alfiov2.command.AdminCommand;
import alfiov2.command.ValidationResult;

import java.util.Collections;
import java.util.List;

public class EventDescriptor {

    public boolean isValid() {
        return false;
    }

    public ValidationResult validate() {
        return ValidationResult.failed();
    }

    public List<AdminCommand> diff(EventDescriptor src) {
        return Collections.emptyList();
    }

    public List<AdminCommand> getCreationCommands() {
        return Collections.emptyList();
    }

}
