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
package alfio.model.api.v1.admin;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Optional;

public enum ApiKeyType {
    API_CLIENT,
    SUPERVISOR,
    SPONSOR,
    OPERATOR;

    public String roleName() {
        return "ROLE_" + name();
    }

    public static Optional<ApiKeyType> safeValueOf(String in) {
        return Arrays.stream(values()).filter(t -> t.name().equals(StringUtils.toRootUpperCase(in)))
            .findFirst();
    }
}
