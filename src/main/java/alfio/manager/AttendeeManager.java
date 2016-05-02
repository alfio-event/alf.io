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
import alfio.manager.support.SponsorAttendeeData;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.repository.EventRepository;
import alfio.repository.SponsorScanRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.UserRepository;
import alfio.util.EventUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;

@Component
public class AttendeeManager {

    private final SponsorScanRepository sponsorScanRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Autowired
    public AttendeeManager(SponsorScanRepository sponsorScanRepository,
                           EventRepository eventRepository,
                           TicketRepository ticketRepository,
                           UserRepository userRepository) {
        this.sponsorScanRepository = sponsorScanRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    public TicketAndCheckInResult registerSponsorScan(String eventShortName, String ticketUid, String username) {
        int userId = userRepository.getByUsername(username).getId();
        Optional<Event> maybeEvent = eventRepository.findOptionalByShortName(eventShortName);
        if(!maybeEvent.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.EVENT_NOT_FOUND, "event not found"));
        }
        Event event = maybeEvent.get();
        Optional<Ticket> maybeTicket = optionally(() -> ticketRepository.findByUUID(ticketUid));
        if(!maybeTicket.isPresent()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "ticket not found"));
        }
        Ticket ticket = maybeTicket.get();
        if(ticket.getStatus() != Ticket.TicketStatus.CHECKED_IN) {
            return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(CheckInStatus.INVALID_TICKET_STATE, "not checked-in"));
        }
        Optional<ZonedDateTime> existingRegistration = sponsorScanRepository.getRegistrationTimestamp(userId, event.getId(), ticket.getId());
        if(!existingRegistration.isPresent()) {
            sponsorScanRepository.insert(userId, ZonedDateTime.now(event.getZoneId()), event.getId(), ticket.getId());
        }
        return new TicketAndCheckInResult(ticket, new DefaultCheckInResult(CheckInStatus.SUCCESS, "success"));
    }

    public Optional<List<SponsorAttendeeData>> retrieveScannedAttendees(String eventShortName, String username, ZonedDateTime start) {
        int userId = userRepository.getByUsername(username).getId();
        Optional<Event> maybeEvent = eventRepository.findOptionalByShortName(eventShortName);
        return maybeEvent.map(event -> loadAttendeesData(event, userId, start));
    }

    private List<SponsorAttendeeData> loadAttendeesData(Event event, int userId, ZonedDateTime start) {
        return sponsorScanRepository.loadSponsorData(event.getId(), userId, start).stream()
            .map(scan -> {
                Ticket ticket = scan.getTicket();
                return new SponsorAttendeeData(ticket.getUuid(), scan.getSponsorScan().getTimestamp().format(EventUtil.JSON_DATETIME_FORMATTER), ticket.getFullName(), ticket.getEmail());
            }).collect(Collectors.toList());
    }

}
