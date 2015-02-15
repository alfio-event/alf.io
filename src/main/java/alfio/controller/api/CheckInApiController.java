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
package alfio.controller.api;

import static alfio.util.OptionalWrapper.*;
import static org.springframework.web.bind.annotation.RequestMethod.*;

import java.util.List;
import java.util.Optional;

import lombok.Data;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import alfio.manager.CheckInManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.Ticket.TicketStatus;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;

@RestController
@RequestMapping("/admin/api")
public class CheckInApiController {

	private final TicketRepository ticketRepository;
	private final EventRepository eventRepository;
	private final CheckInManager checkInManager;

	@Data
	public static class TicketCode {
		private String code;
	}
	
	public enum CheckInStatus {
		EVENT_NOT_FOUND, TICKET_NOT_FOUND, EMPTY_TICKET_CODE, INVALID_TICKET_CODE, INVALID_TICKET_STATE, ALREADY_CHECK_IN, MUST_PAY, SUCCESS
	}
	
	@Data
	public static class CheckInResult {
		private final CheckInStatus status;
		private final String message;
	}
	
	@Data
	public static class OnSitePaymentConfirmation {
		private final boolean status;
		private final String message;
	}

	@Autowired
	public CheckInApiController(EventRepository eventRepository, TicketRepository ticketRepository, CheckInManager checkInManager) {
		this.eventRepository = eventRepository;
		this.ticketRepository = ticketRepository;
		this.checkInManager = checkInManager;
	}
	
	@RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}", method = GET)
	public Ticket findTicketWithUUID(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier) {
		Optional<Event> event = optionally(() -> eventRepository.findById(eventId));
		Optional<Ticket> ticket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));
		
		if(event.isPresent() && ticket.isPresent()) {
			return ticket.get();
		} else {
			return null;
		}
	}

	@RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}", method = POST)
	public CheckInResult checkIn(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier, @RequestBody TicketCode ticketCode) {

		Optional<Event> event = optionally(() -> eventRepository.findById(eventId));

		if (!event.isPresent()) {
			return new CheckInResult(CheckInStatus.EVENT_NOT_FOUND, "Event with id " + eventId + " not found");
		}

		Optional<Ticket> ticket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));
		
		if (!ticket.isPresent()) {
			return new CheckInResult(CheckInStatus.TICKET_NOT_FOUND, "Ticket with uuid " + ticketIdentifier + " not found");
		}
		
		if(ticketCode == null || StringUtils.isEmpty(ticketCode.getCode())) {
			return new CheckInResult(CheckInStatus.EMPTY_TICKET_CODE, "Missing ticket code");
		}
		
		return handleCheckIn(event.get(), ticket.get(), ticketCode.getCode());
	}
	
	
	private CheckInResult handleCheckIn(Event event, Ticket ticket, String ticketCode) {
		
		if (!ticketCode.equals(ticket.ticketCode(event.getPrivateKey()))) {
			return new CheckInResult(CheckInStatus.INVALID_TICKET_CODE, "Ticket qr code does not match");
		}
		
		final TicketStatus ticketStatus = ticket.getStatus();
		
		if (ticketStatus == TicketStatus.TO_BE_PAID) {
			return new CheckInResult(CheckInStatus.MUST_PAY, "Must pay for ticket"); //TODO: must say how much
		}
		
		if (ticketStatus == TicketStatus.CHECKED_IN) {
			return new CheckInResult(CheckInStatus.ALREADY_CHECK_IN, "Error: already checked in");
		}
		
		if (ticket.getStatus() != TicketStatus.ACQUIRED) {
			return new CheckInResult(CheckInStatus.INVALID_TICKET_STATE, "Invalid ticket state, expected ACQUIRED state, received " + ticket.getStatus());
		}
		
		checkInManager.checkIn(ticket.getUuid());
		return new CheckInResult(CheckInStatus.SUCCESS, "success");
	}
	
	@RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}/confirm-on-site-payment", method = POST)
	public OnSitePaymentConfirmation confirmOnSitePayment(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier) {
		
		Optional<Ticket> ticket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));
		
		if (!ticket.isPresent()) {
			return new OnSitePaymentConfirmation(false, "Ticket with uuid " + ticketIdentifier + " not found");
		}
		
		Ticket t = ticket.get();
		
		if (t.getStatus() != TicketStatus.TO_BE_PAID) {
			return new OnSitePaymentConfirmation(false, "Invalid ticket state, expected TO_BE_PAID state, received " + t.getStatus());
		}
		
		checkInManager.acquire(t.getUuid());
		return new OnSitePaymentConfirmation(true, "ok");
	}
	
	@RequestMapping(value = "/check-in/{eventId}/ticket", method = GET)
	public List<Ticket> listAllTickets(@PathVariable("eventId") int eventId) {
		return ticketRepository.findAllByEventId(eventId);
	}
}
