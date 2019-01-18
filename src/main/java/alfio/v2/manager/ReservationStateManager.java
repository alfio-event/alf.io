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

import alfio.v2.model.ReservationIdentifier;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class ReservationStateManager {


    //private Map<Pair<ReservationState, ReservationState>, List<Action>>


    public ReservationIdentifier create(/*...... parameters to create a new reservation ...... ?*/) {
        // first step is a special case (direct reservation creation), then we call the List<Action>

        return null;
    }

    public void /*Result...*/ transitionToPending(ReservationIdentifier reservation, ReservationIdentifier.ReservationContext ctx) {
        // check pre condition


        // call...
    }

    public void /*Result...*/ transitionToComplete(ReservationIdentifier reservation, ReservationIdentifier.ReservationContext ctx) {
    }


    //...

    public void /*Result...*/ cancel(ReservationIdentifier reservation, ReservationIdentifier.ReservationContext ctx) {
    }
}
