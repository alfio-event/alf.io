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

import alfio.model.Event;
import alfio.model.Ticket;
import alfio.repository.EventRepository;
import alfio.repository.SponsorScanRepository;
import alfio.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Optional;

import static alfio.util.OptionalWrapper.optionally;

@Component
public class AttendeeManager {

    private final SponsorScanRepository sponsorScanRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    @Autowired
    public AttendeeManager(SponsorScanRepository sponsorScanRepository, EventRepository eventRepository, TicketRepository ticketRepository) {
        this.sponsorScanRepository = sponsorScanRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
    }

    public Optional<ZonedDateTime> registerSponsorScan(int eventId, String ticketUid) {
        Event event = optionally(() -> eventRepository.findById(eventId)).orElseThrow(IllegalArgumentException::new);
        Ticket ticket = optionally(() -> ticketRepository.findByUUID(ticketUid)).orElseThrow(IllegalArgumentException::new);
        Optional<ZonedDateTime> existingRegistration = sponsorScanRepository.getRegistrationTimestamp(event.getId(), ticket.getUuid());
        if(!existingRegistration.isPresent()) {
            sponsorScanRepository.insert(ZonedDateTime.now(event.getZoneId()), event.getId(), ticket.getUuid());
            return Optional.empty();
        }
        return existingRegistration.map(d -> d.withZoneSameInstant(event.getZoneId()));
    }
}
