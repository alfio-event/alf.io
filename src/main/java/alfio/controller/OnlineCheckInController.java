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
import alfio.manager.ExtensionManager;
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

import static alfio.manager.support.CheckInStatus.ALREADY_CHECK_IN;
import static alfio.manager.support.CheckInStatus.SUCCESS;
import static alfio.util.EventUtil.findMatchingLink;

@Controller
@AllArgsConstructor
@Log4j2
public class OnlineCheckInController {

    private final TicketReservationManager ticketReservationManager;
    private final CheckInManager checkInManager;
    private final ExtensionManager extensionManager;

    @GetMapping("/event/{shortName}/ticket/{ticketUUID}/check-in/{ticketCodeHash}")
    public String performCheckIn(@PathVariable("shortName") String eventShortName,
                                 @PathVariable("ticketUUID") String ticketUUID,
                                 @PathVariable("ticketCodeHash") String ticketCodeHash) {

        return ticketReservationManager.fetchCompleteAndAssignedForOnlineCheckIn(eventShortName, ticketUUID)
            .flatMap(data -> {
                var ticket = data.getTicket();
                var event = data.getEventWithCheckInInfo();
                String ticketCode = ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive());
                if(MessageDigest.isEqual(DigestUtils.sha256Hex(ticketCode).getBytes(StandardCharsets.UTF_8), ticketCodeHash.getBytes(StandardCharsets.UTF_8))) {
                    log.debug("code successfully validated for ticket {}", ticketUUID);
                    // check-in can be done. Let's check if there is a redirection URL
                    var categoryConfiguration = data.getCategoryMetadata().getOnlineConfiguration();
                    var eventConfiguration = event.getMetadata().getOnlineConfiguration();
                    var match = findMatchingLink(event.getZoneId(), categoryConfiguration, eventConfiguration);
                    if(match.isPresent()) {
                        var checkInStatus = checkInManager.performCheckinForOnlineEvent(ticket, event, data.getTicketCategory());
                        log.info("check-in status {} for ticket {}", checkInStatus, ticketUUID);
                        if(checkInStatus == SUCCESS || (checkInStatus == ALREADY_CHECK_IN && ticket.isCheckedIn())) {
                            // invoke the extension for customizing the URL, if any
                            // we call the extension from here because it will have a smaller impact on the throughput compared to
                            // calling it from the checkInManager
                            var customUrlOptional = extensionManager.handleOnlineCheckInLink(match.get(), ticket, event, data.getTicketAdditionalInfo());
                            return customUrlOptional.or(() -> match);
                        }
                        log.info("denying check-in for ticket {} because check-in status was {}", ticketUUID, checkInStatus);
                        return Optional.of("/event/"+event.getShortName()+"/ticket/"+ticketUUID+"/update");
                    }
                    log.info("validation was successful, but cannot find a valid link for {}", ticketUUID);
                    return Optional.of("/event/"+event.getShortName()+"/ticket/"+ticketUUID+"/check-in/"+ticketCodeHash+"/waiting-room");
                }
                log.warn("code validation failed for ticket {}", ticketUUID);
                return Optional.empty();
            })
            .map(link -> "redirect:"+link)
            .orElse("redirect:/");
    }
}
