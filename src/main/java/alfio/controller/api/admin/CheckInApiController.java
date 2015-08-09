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
package alfio.controller.api.admin;

import alfio.manager.CheckInManager;
import alfio.model.Event;
import alfio.model.FullTicketInfo;
import alfio.model.Ticket;
import alfio.model.Ticket.TicketStatus;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static alfio.util.OptionalWrapper.optionally;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Log4j2
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
        EVENT_NOT_FOUND, TICKET_NOT_FOUND, EMPTY_TICKET_CODE, INVALID_TICKET_CODE, INVALID_TICKET_STATE, ALREADY_CHECK_IN, MUST_PAY, OK_READY_TO_BE_CHECKED_IN, SUCCESS
    }

    @Data
    public static class TicketAndCheckInResult {
        private final Ticket ticket;
        private final CheckInResult result;
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
    public TicketAndCheckInResult findTicketWithUUID(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier, @RequestParam("qrCode") String qrCode) {

        CheckInResult result = extractStatus(eventId, ticketIdentifier, qrCode);

        Optional<Event> event = optionally(() -> eventRepository.findById(eventId));
        Optional<Ticket> ticket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));

        if(event.isPresent() && ticket.isPresent()) {
            return new TicketAndCheckInResult(ticket.get(), result);
        } else {
            return new TicketAndCheckInResult(null, result);
        }
    }

    private CheckInResult extractStatus(int eventId, String ticketIdentifier, String ticketCode) {
        Optional<Event> maybeEvent = optionally(() -> eventRepository.findById(eventId));

        if (!maybeEvent.isPresent()) {
            return new CheckInResult(CheckInStatus.EVENT_NOT_FOUND, "Event with id " + eventId + " not found");
        }

        Optional<Ticket> maybeTicket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));

        if (!maybeTicket.isPresent()) {
            return new CheckInResult(CheckInStatus.TICKET_NOT_FOUND, "Ticket with uuid " + ticketIdentifier + " not found");
        }

        if(StringUtils.isEmpty(ticketCode)) {
            return new CheckInResult(CheckInStatus.EMPTY_TICKET_CODE, "Missing ticket code");
        }

        Ticket ticket = maybeTicket.get();
        Event event = maybeEvent.get();

        log.info("scanned code is {}", ticketCode);
        log.info("true code    is {}", ticket.ticketCode(event.getPrivateKey()));

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

        return new CheckInResult(CheckInStatus.OK_READY_TO_BE_CHECKED_IN, "Ready to be checked in");
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}", method = POST)
    public TicketAndCheckInResult checkIn(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier, @RequestBody TicketCode ticketCode) {
        CheckInResult status = extractStatus(eventId, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode).orElse(null));
        if(status.getStatus() == CheckInStatus.OK_READY_TO_BE_CHECKED_IN) {
            checkInManager.checkIn(ticketIdentifier);
            return new TicketAndCheckInResult(ticketRepository.findByUUID(ticketIdentifier), new CheckInResult(CheckInStatus.SUCCESS, "success"));
        }
        return new TicketAndCheckInResult(optionally(() -> ticketRepository.findByUUID(ticketIdentifier)).orElse(null), status);
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}/manual-check-in", method = POST)
    public boolean manualCheckIn(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier) {
        log.warn("for event id : {} and ticket : {}, a manual check in has been done", eventId, ticketIdentifier);

        Optional<Ticket> ticket = optionally(() -> ticketRepository.findByUUID(ticketIdentifier));
        return ticket.map((t) -> {

            if(t.getStatus() == TicketStatus.TO_BE_PAID) {
                checkInManager.acquire(ticketIdentifier);
            }

            checkInManager.checkIn(ticketIdentifier);
            return true;
        }).orElse(false);
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
    public List<FullTicketInfo> listAllTickets(@PathVariable("eventId") int eventId) {
        return ticketRepository.findAllFullTicketInfoAssignedByEventId(eventId);
    }
}
