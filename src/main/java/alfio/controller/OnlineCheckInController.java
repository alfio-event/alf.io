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
package alfio.controller;

import alfio.manager.CheckInManager;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.metadata.CallLink;
import alfio.model.metadata.OnlineConfiguration;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Optional;

@Controller
@AllArgsConstructor
@Log4j2
public class OnlineCheckInController {

    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketReservationManager ticketReservationManager;
    private final CheckInManager checkInManager;

    @GetMapping("/event/{shortName}/ticket/{ticketUUID}/check-in/{ticketCodeHash}")
    public String performCheckIn(@PathVariable("shortName") String eventShortName,
                                 @PathVariable("ticketUUID") String ticketUUID,
                                 @PathVariable("ticketCodeHash") String ticketCodeHash) {

        return ticketReservationManager.fetchCompleteAndAssigned(eventShortName, ticketUUID)
            .filter(triple -> triple.getLeft().getIsOnline()) // this check-in is allowed only for online events
            .flatMap(triple -> {
                var ticket = triple.getRight();
                var event = triple.getLeft();
                String ticketCode = ticket.ticketCode(event.getPrivateKey());
                if(MessageDigest.isEqual(DigestUtils.sha256(ticketCode.getBytes(StandardCharsets.UTF_8)), ticketCodeHash.getBytes(StandardCharsets.UTF_8))) {
                    log.debug("code successfully validated for ticket {}", ticketUUID);
                    // check-in can be done. Let's check if there is a redirection URL
                    var categoryConfiguration = ticketCategoryRepository.getMetadata(event.getId(), ticket.getCategoryId()).getOnlineConfiguration();
                    var eventConfiguration = eventRepository.getMetadataForEvent(event.getId()).getOnlineConfiguration();

                    var match = findBestMatch(event, categoryConfiguration, eventConfiguration);
                    if(match.isPresent()) {
                        var status = checkInManager.checkIn(event.getId(), ticketUUID, Optional.of(ticketCode), ticketUUID);
                        log.info("check-in status {} for ticket {}", status.getResult().getStatus(), ticketUUID);
                        return match;
                    }
                    log.info("validation was successful, but cannot find a valid link for {}", ticketUUID);
                    return Optional.of("/event/"+event.getShortName()+"/ticket/"+ticketUUID+"/update");
                }
                log.warn("code validation failed for ticket {}", ticketUUID);
                return Optional.empty();
            })
            .map(link -> "redirect:"+link)
            .orElse("redirect:/");
    }

    private static Optional<String> findBestMatch(Event event, OnlineConfiguration categoryConfiguration, OnlineConfiguration eventConfiguration) {
        var zoneId = event.getZoneId();
        var now = ZonedDateTime.now(zoneId);
        return firstMatch(categoryConfiguration, zoneId, now)
            .or(() -> firstMatch(eventConfiguration, zoneId, now))
            .map(CallLink::getLink);
    }

    private static Optional<CallLink> firstMatch(OnlineConfiguration onlineConfiguration, ZoneId zoneId, ZonedDateTime now) {
        return Optional.ofNullable(onlineConfiguration).stream()
            .flatMap(configuration -> configuration.getCallLinks().stream())
            .sorted(Comparator.comparing(CallLink::getValidFrom).reversed())
            .filter(callLink -> now.isBefore(callLink.getValidTo().atZone(zoneId)) && now.plusSeconds(1).isAfter(callLink.getValidFrom().atZone(zoneId)))
            .findFirst();
    }

}
