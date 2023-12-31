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
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.support.TicketWithAdditionalFields;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.EventRepository;
import alfio.repository.SponsorScanRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class AttendeeManager {

    public static final String DEFAULT_OPERATOR_ID = "__DEFAULT__";
    private final SponsorScanRepository sponsorScanRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final UserManager userManager;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final ClockProvider clockProvider;

    public TicketAndCheckInResult registerSponsorScan(String eventShortName,
                                                      String ticketUid,
                                                      String notes,
                                                      SponsorScan.LeadStatus leadStatus,
                                                      String username,
                                                      String operatorId,
                                                      Long timestamp) {
        int userId = userRepository.getByUsername(username).getId();
        Optional<EventAndOrganizationId> maybeEvent = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventShortName);
        if(maybeEvent.isEmpty()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.EVENT_NOT_FOUND, "event not found"));
        }
        EventAndOrganizationId event = maybeEvent.get();
        Optional<Ticket> maybeTicket = ticketRepository.findOptionalByUUID(ticketUid);
        if(maybeTicket.isEmpty()) {
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "ticket not found"));
        }
        Ticket ticket = maybeTicket.get();
        if(ticket.getStatus() != Ticket.TicketStatus.CHECKED_IN || ticket.getEventId() != event.getId()) {
            return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(CheckInStatus.INVALID_TICKET_STATE, "not checked-in"));
        }
        var operator = Objects.requireNonNullElse(operatorId, DEFAULT_OPERATOR_ID);
        Optional<ZonedDateTime> existingRegistration = sponsorScanRepository.getRegistrationTimestamp(userId, event.getId(), ticket.getId(), operator);
        if(existingRegistration.isEmpty()) {
            ZoneId eventZoneId = eventRepository.getZoneIdByEventId(event.getId());
            var creation = timestamp != null ? Instant.ofEpochMilli(timestamp).atZone(eventZoneId) : ZonedDateTime.now(clockProvider.withZone(eventZoneId));
            sponsorScanRepository.insert(userId, creation, event.getId(), ticket.getId(), notes, leadStatus, operator);
        } else {
            sponsorScanRepository.updateNotesAndLeadStatus(userId, event.getId(), ticket.getId(), notes, leadStatus, operator);
        }
        return new TicketAndCheckInResult(new TicketWithCategory(ticket, null), new DefaultCheckInResult(CheckInStatus.SUCCESS, "success"));
    }

    public Result<TicketWithAdditionalFields> retrieveTicket(String eventShortName, String ticketUid, String username) {
        Optional<Event> maybeEvent = eventRepository.findOptionalByShortName(eventShortName)
            .filter(e -> userManager.findUserOrganizations(username).stream().anyMatch(o -> o.getId() == e.getOrganizationId()));

        if(maybeEvent.isEmpty()) {
            return Result.error(ErrorCode.EventError.NOT_FOUND);
        }

        Event event = maybeEvent.get();
        Optional<Ticket> maybeTicket = ticketRepository.findOptionalByUUID(ticketUid).filter(t -> t.getEventId() == event.getId());

        return new Result.Builder<TicketWithAdditionalFields>()
            .checkPrecondition(maybeTicket::isPresent, ErrorCode.custom("ticket_not_found", "ticket not found"))
            .build(() -> {
                var ticket = maybeTicket.orElseThrow();
                var descriptionAndValues = EventUtil.retrieveFieldValues(ticketRepository, purchaseContextFieldManager, additionalServiceItemRepository, true).apply(ticket, event);
                return new TicketWithAdditionalFields(ticket, descriptionAndValues);
            });
    }

    public Optional<List<SponsorAttendeeData>> retrieveScannedAttendees(String eventShortName, String username, ZonedDateTime start) {
        int userId = userRepository.getByUsername(username).getId();
        Optional<Event> maybeEvent = eventRepository.findOptionalByShortName(eventShortName);
        return maybeEvent.map(event -> loadAttendeesData(event, userId, start));
    }

    private List<SponsorAttendeeData> loadAttendeesData(EventAndOrganizationId event, int userId, ZonedDateTime start) {
        return sponsorScanRepository.loadSponsorData(event.getId(), userId, start).stream()
            .map(scan -> {
                Ticket ticket = scan.getTicket();
                return new SponsorAttendeeData(ticket.getUuid(), scan.getSponsorScan().getTimestamp().format(EventUtil.JSON_DATETIME_FORMATTER), ticket.getFullName(), ticket.getEmail());
            })
            .distinct()
            .collect(Collectors.toList());
    }

}
