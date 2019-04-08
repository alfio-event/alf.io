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
package alfio.model.transaction.capabilities;

import alfio.model.TicketReservationWithTransaction;
import alfio.model.result.Result;
import alfio.model.transaction.Capability;
import alfio.model.transaction.PaymentContext;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public interface OfflineProcessor extends Capability {
    /**
     * Verify if the given reservations have been paid according to the payment provider.
     *
     * @param reservations the reservations to check
     * @param paymentContext
     * @param lastCheck last check timestamp, can be null. Defaults to <code>now - 24h</code>
     * @return a list of {@link alfio.model.TicketReservation} ids.
     */
    Result<List<String>> checkPendingReservations(Collection<TicketReservationWithTransaction> reservations,
                                                                            PaymentContext paymentContext,
                                                                            ZonedDateTime lastCheck);

}
