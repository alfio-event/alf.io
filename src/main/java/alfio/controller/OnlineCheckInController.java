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
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static alfio.manager.support.CheckInStatus.SUCCESS;
import static alfio.util.EventUtil.findMatchingLink;

@Controller
@AllArgsConstructor
@Log4j2
public class OnlineCheckInController {

    private final TicketReservationManager ticketReservationManager;
    private final CheckInManager checkInManager;

    @GetMapping("/event/{shortName}/ticket/{ticketUUID}/check-in/{ticketCodeHash}")
    public String performCheckIn(@PathVariable("shortName") String eventShortName,
                                 @PathVariable("ticketUUID") String ticketUUID,
                                 @PathVariable("ticketCodeHash") String ticketCodeHash) {

        return ticketReservationManager.fetchCompleteAndAssignedForOnlineCheckIn(eventShortName, ticketUUID)
            .flatMap(info -> {
                var ticket = info.getTicket();
                var event = info.getEventWithCheckInInfo();
                String ticketCode = ticket.ticketCode(event.getPrivateKey());
                if(MessageDigest.isEqual(DigestUtils.sha256Hex(ticketCode).getBytes(StandardCharsets.UTF_8), ticketCodeHash.getBytes(StandardCharsets.UTF_8))) {
                    log.debug("code successfully validated for ticket {}", ticketUUID);
                    // check-in can be done. Let's check if there is a redirection URL
                    var categoryConfiguration = info.getCategoryMetadata().getOnlineConfiguration();
                    var eventConfiguration = event.getMetadata().getOnlineConfiguration();
                    var match = findMatchingLink(event.getZoneId(), categoryConfiguration, eventConfiguration);
                    if(match.isPresent()) {
                        var checkInStatus = checkInManager.performCheckinForOnlineEvent(ticket, event);
                        log.info("check-in status {} for ticket {}", checkInStatus, ticketUUID);
                        if(checkInStatus == SUCCESS) {
                            return match;
                        }
                        log.info("denying check-in for ticket {} because check-in status was {}", ticketUUID, checkInStatus);
                        return Optional.of("/event/"+event.getShortName()+"/ticket/"+ticketUUID+"/update");
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

}
