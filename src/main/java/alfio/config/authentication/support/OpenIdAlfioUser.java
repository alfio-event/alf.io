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
package alfio.config.authentication.support;

import alfio.model.user.Role;
import alfio.model.user.User;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public record OpenIdAlfioUser(
    String idToken,
    String subject,
    String email,
    User.Type userType,
    Set<Role> alfioRoles,
    Map<String, Set<String>> alfioOrganizationAuthorizations
) implements Serializable {

    public boolean isAdmin() {
        return userType == User.Type.INTERNAL && alfioRoles.contains(Role.ADMIN);
    }

    public boolean isPublicUser() {
        return userType == User.Type.PUBLIC;
    }
}
