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

import alfio.manager.support.CheckInStatus;
import alfio.manager.support.DefaultCheckInResult;
import alfio.manager.support.OnSitePaymentResult;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.model.Event;
import alfio.model.FullTicketInfo;
import alfio.model.Ticket;
import alfio.model.Ticket.TicketStatus;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.util.MonetaryUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static alfio.manager.support.CheckInStatus.*;
import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional
@Log4j2
public class CheckInManager {

    private final TicketRepository ticketRepository;
    private final EventRepository eventRepository;

    @Autowired
    public CheckInManager(TicketRepository ticketRepository, EventRepository eventRepository) {
        this.ticketRepository = ticketRepository;
        this.eventRepository = eventRepository;
    }


    private void checkIn(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.ACQUIRED);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.CHECKED_IN.toString());
    }

    private void acquire(String uuid) {
        Validate.isTrue(ticketRepository.findByUUID(uuid).getStatus() == TicketStatus.TO_BE_PAID);
        ticketRepository.updateTicketStatusWithUUID(uuid, TicketStatus.ACQUIRED.toString());
    }

    public TicketAndCheckInResult confirmOnSitePayment(String eventName, String ticketIdentifier, Optional<String> ticketCode) {
        return eventRepository.findOptionalByShortName(eventName)
            .flatMap(e -> confirmOnSitePayment(ticketIdentifier).map((String s) -> Pair.of(s, e)))
            .map(p -> checkIn(p.getRight().getId(), ticketIdentifier, ticketCode))
            .orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "")));
    }

    public Optional<String> confirmOnSitePayment(String ticketIdentifier) {
        Optional<String> uuid = findAndLockTicket(ticketIdentifier)
            .filter(t -> t.getStatus() == TicketStatus.TO_BE_PAID)
            .map(Ticket::getUuid);

        uuid.ifPresent(this::acquire);
        return uuid;
    }

    public TicketAndCheckInResult checkIn(String shortName, String ticketIdentifier, Optional<String> ticketCode) {
        return eventRepository.findOptionalByShortName(shortName).map(e -> checkIn(e.getId(), ticketIdentifier, ticketCode)).orElseGet(() -> new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.EVENT_NOT_FOUND, "event not found")));
    }

    public TicketAndCheckInResult checkIn(int eventId, String ticketIdentifier, Optional<String> ticketCode) {
        TicketAndCheckInResult descriptor = extractStatus(eventId, ticketRepository.findByUUIDForUpdate(ticketIdentifier), ticketIdentifier, ticketCode);
        if(descriptor.getResult().getStatus() == OK_READY_TO_BE_CHECKED_IN) {
            checkIn(ticketIdentifier);
            return new TicketAndCheckInResult(descriptor.getTicket(), new DefaultCheckInResult(SUCCESS, "success"));
        }
        return descriptor;
    }

    public boolean manualCheckIn(String ticketIdentifier) {
        Optional<Ticket> ticket = findAndLockTicket(ticketIdentifier);
        return ticket.map((t) -> {

            if(t.getStatus() == TicketStatus.TO_BE_PAID) {
                acquire(ticketIdentifier);
            }

            checkIn(ticketIdentifier);
            return true;
        }).orElse(false);
    }

    private Optional<Ticket> findAndLockTicket(String uuid) {
        return ticketRepository.findByUUIDForUpdate(uuid);
    }

    public List<FullTicketInfo> findAllFullTicketInfo(int eventId) {
        return ticketRepository.findAllFullTicketInfoAssignedByEventId(eventId);
    }

    public TicketAndCheckInResult evaluateTicketStatus(int eventId, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(optionally(() -> eventRepository.findById(eventId)), optionally(() -> ticketRepository.findByUUID(ticketIdentifier)), ticketIdentifier, ticketCode);
    }

    public TicketAndCheckInResult evaluateTicketStatus(String eventName, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(eventRepository.findOptionalByShortName(eventName), optionally(() -> ticketRepository.findByUUID(ticketIdentifier)), ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(int eventId, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {
        return extractStatus(optionally(() -> eventRepository.findById(eventId)), maybeTicket, ticketIdentifier, ticketCode);
    }

    private TicketAndCheckInResult extractStatus(Optional<Event> maybeEvent, Optional<Ticket> maybeTicket, String ticketIdentifier, Optional<String> ticketCode) {

        if (!maybeEvent.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EVENT_NOT_FOUND, "Event not found"));
        }

        if (!maybeTicket.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(TICKET_NOT_FOUND, "Ticket with uuid " + ticketIdentifier + " not found"));
        }

        if(!ticketCode.filter(StringUtils::isNotEmpty).isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(EMPTY_TICKET_CODE, "Missing ticket code"));
        }

        Ticket ticket = maybeTicket.get();
        Event event = maybeEvent.get();
        String code = ticketCode.get();

        log.trace("scanned code is {}", code);
        log.trace("true code    is {}", ticket.ticketCode(event.getPrivateKey()));

        if (!code.equals(ticket.ticketCode(event.getPrivateKey()))) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(INVALID_TICKET_CODE, "Ticket qr code does not match"));
        }

        final TicketStatus ticketStatus = ticket.getStatus();

        if (ticketStatus == TicketStatus.TO_BE_PAID) {
            return new TicketAndCheckInResult(ticket, new OnSitePaymentResult(MUST_PAY, "Must pay for ticket", MonetaryUtil.addVAT(MonetaryUtil.centsToUnit(ticket.getFinalPriceCts()), event.getVat()), event.getCurrency()));
        }

        if (ticketStatus == TicketStatus.CHECKED_IN) {
            return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(ALREADY_CHECK_IN, "Error: already checked in"));
        }

        if (ticket.getStatus() != TicketStatus.ACQUIRED) {
            return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(INVALID_TICKET_STATE, "Invalid ticket state, expected ACQUIRED state, received " + ticket.getStatus()));
        }

        return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(OK_READY_TO_BE_CHECKED_IN, "Ready to be checked in"));
    }

}
