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
package alfio.controller.api.pass;

import alfio.manager.PassKitManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * https://developer.apple.com/library/archive/documentation/PassKit/Reference/PassKit_WebService/WebService.html
 */
@RestController
@RequestMapping("/api/pass/event/{eventName}/v1")
@Log4j2
@RequiredArgsConstructor
public class PassKitApiController {

    private final PassKitManager passKitManager;


    @GetMapping("/version/passes/{passTypeIdentifier}/{serialNumber}")
    public void getLatestVersion(@PathVariable("eventName") String eventName,
                                 @PathVariable("passTypeIdentifier") String passTypeIdentifier,
                                 @PathVariable("serialNumber") String serialNumber,
                                 @RequestHeader("Authorization") String authorization,
                                 HttpServletResponse response) throws IOException {
        Optional<Pair<EventAndOrganizationId, Ticket>> validationResult = passKitManager.validateToken(eventName, passTypeIdentifier, serialNumber, authorization);
        if(validationResult.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            Pair<EventAndOrganizationId, Ticket> pair = validationResult.get();
            writePassResponse(response, pair.getLeft(), pair.getRight(), false);
        }
    }

    @GetMapping("/version/passes/{ticketUuid}")
    public void downloadPassForTicket(@PathVariable("eventName") String eventName,
                                      @PathVariable("ticketUuid") String ticketUuid,
                                      HttpServletResponse response) throws IOException {
        var ticketAndEventData = passKitManager.retrieveTicketDetails(eventName, ticketUuid);
        if (ticketAndEventData.isPresent()) {
            var pair = ticketAndEventData.get();
            writePassResponse(response, pair.getKey(), pair.getValue(), true);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void writePassResponse(HttpServletResponse response,
                                   EventAndOrganizationId eventAndOrganizationId,
                                   Ticket ticket,
                                   boolean addFilename) throws IOException {
        try (var os = response.getOutputStream()) {
            response.setContentType("application/vnd.apple.pkpass");
            if (addFilename) {
                response.setHeader("Content-Disposition", "attachment; filename=Passbook-"+ticket.getUuid().substring(0, 8)+".pkpass");
            }
            passKitManager.writePass(ticket, eventAndOrganizationId, os);
        } catch (Exception e) {
            log.warn("Error during pass generation", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }


    // not (yet) implemented APIs. These are no-op for now.

    @GetMapping("/devices/*/registrations/*")
    public ResponseEntity<Void> getRegisteredPasses() {
        log.trace("getRegisteredPasses called. Returning 204");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices/*/registrations/*/*")
    public ResponseEntity<Void> register() {
        log.trace("register called. Returning 200");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/devices/*/registrations/*/*")
    public ResponseEntity<Void> deleteRegistration() {
        log.trace("deleteRegistration called. Returning 200");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/log")
    public ResponseEntity<Void> log() {
        log.trace("log called. Returning 200");
        return ResponseEntity.ok().build();
    }

}
