/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller;

import static io.bagarino.util.OptionalWrapper.optionally;
import io.bagarino.model.Ticket;
import io.bagarino.repository.EventRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.TicketReservationRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class TicketController {
	
	private final EventRepository eventRepository;
	private final TicketReservationRepository ticketReservationRepository;
	private final TicketRepository ticketRepository;
	
	@Autowired
	public TicketController(EventRepository eventRepository, TicketReservationRepository ticketReservationRepository, TicketRepository ticketRepository) {
		this.eventRepository = eventRepository;
		this.ticketReservationRepository = ticketReservationRepository;
		this.ticketRepository = ticketRepository;
	}
	
	

	@RequestMapping(value = "/event/{eventId}/reservation/{reservationId}/download-ticket/{ticketIdentifier}", method = RequestMethod.GET)
	public void generateTicketPdf(@PathVariable("eventId") int eventId, @PathVariable("reservationId") String reservationId, @PathVariable("ticketIdentifier") String ticketIdentifier) {
		
		optionally(() -> eventRepository.findById(eventId)).orElseThrow(IllegalArgumentException::new);
		optionally(() -> ticketReservationRepository.findReservationById(reservationId)).orElseThrow(IllegalArgumentException::new);
		
		Ticket t = optionally(() ->ticketRepository.findByUUID(ticketIdentifier)).orElseThrow(IllegalArgumentException::new);
		
		//TODO: here generate PDF
	}
}
