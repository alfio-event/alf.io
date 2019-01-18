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
package alfio.v2.model;

import lombok.AllArgsConstructor;

import java.security.Principal;
import java.util.UUID;

public class ReservationIdentifier {


    @AllArgsConstructor
    public static class ReservationContext {
        private final UUID reservationId;
        private final long eventId;
        private final long organizationId;

        private final Principal principal;
    }
}
