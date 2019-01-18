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
package alfio.v2.manager;


//
// ideally everything should begin here
// -> the user create a reservation request, that may or may not be fulfilled -> (if not fulfilled -> waiting queue).
// -> this state manager is partially the current WaitingQueue.


// -> add InvoiceRequest table + job for async invoice generation support (for handling external system failures)

import alfio.v2.model.ReservationIdentifier;

import java.util.Optional;

public class ReservationRequestStateManager {

    public ReservationResult createRequest() {
        return  null;
    }

    public static class ReservationResult {

        // identifier
        // status
        // reservation request detail

        public Optional<ReservationIdentifier> getReservation() {
            return Optional.empty();
        }
    }
}
