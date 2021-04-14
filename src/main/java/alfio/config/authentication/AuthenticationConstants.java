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
package alfio.config.authentication;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AuthenticationConstants {
    public static final String OPERATOR = "OPERATOR";
    public static final String SPONSOR = "SPONSOR";
    static final String ADMIN_API = "/admin/api";
    static final String ADMIN_PUBLIC_API = "/api/v1/admin";
    static final String SUPERVISOR = "SUPERVISOR";
    static final String ADMIN = "ADMIN";
    static final String OWNER = "OWNER";
    static final String API_CLIENT = "API_CLIENT";
    static final String X_REQUESTED_WITH = "X-Requested-With";
}
