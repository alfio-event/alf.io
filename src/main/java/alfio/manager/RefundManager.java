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
package alfio.manager;

import alfio.model.TicketReservation;
import alfio.repository.TicketReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
@Component
public class RefundManager {

    private final TicketReservationRepository ticketReservationRepository;

    @Autowired
    public RefundManager(TicketReservationRepository ticketReservationRepository) {
        this.ticketReservationRepository = ticketReservationRepository;
    }

    public void fullRefund(String reservationId, String username) {
        //FIXME check username has role for doing it
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
    }

    public void partialRefund(String reservationId, List<String> ticketsToBeRefunded, String username) {
        //FIXME check username has role for doing it
        TicketReservation reservation = ticketReservationRepository.findReservationById(reservationId);
    }
}
