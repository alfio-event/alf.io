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
package alfio.model.user;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum Role {
    ADMIN("ROLE_ADMIN", "Administrator"),
    OWNER("ROLE_OWNER", "Organization owner"),
    SUPERVISOR("ROLE_SUPERVISOR", "Check-in supervisor"),
    OPERATOR("ROLE_OPERATOR", "Check-in operator"),
    SPONSOR("ROLE_SPONSOR", "Sponsor"),
    API_CONSUMER("ROLE_API_CLIENT", "API Client");

    private final String roleName;
    private final String description;

    Role(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    public static Role fromRoleName(String roleName) {
        return Arrays.stream(values()).filter(r -> r.getRoleName().equals(roleName)).findFirst().orElse(OPERATOR);
    }
}
