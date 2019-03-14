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

import alfio.manager.payment.PaymentSpecification;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.TicketReservation;
import alfio.model.TotalPrice;
import alfio.model.transaction.Capability;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface ExternalProcessing extends Capability {
    /**
     * Builds a {@link PaymentSpecification} from the request params in the given context (Event, TicketReservation).
     * This must be implemented if the payment provider supports external processing
     *
     * @param event
     * @param reservation
     * @param reservationCost
     * @param orderSummary
     * @throws IllegalStateException if the payment provider supports external processing and does not override the default implementation
     * @return a {@link Function} for converting the request params to a {@link PaymentSpecification}
     */
    Function<Map<String, List<String>>, PaymentSpecification> getSpecificationFromRequest(Event event,
                                                                                          TicketReservation reservation,
                                                                                          TotalPrice reservationCost,
                                                                                          OrderSummary orderSummary);

}
