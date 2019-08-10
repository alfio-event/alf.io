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
package alfio.controller.api.v1;

import alfio.manager.AttendeeManager;
import alfio.manager.support.SponsorAttendeeData;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.model.result.Result;
import alfio.model.support.TicketWithAdditionalFields;
import alfio.repository.SponsorScanRepository;
import alfio.util.EventUtil;
import alfio.util.Wrappers;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendees")
@Log4j2
public class AttendeeApiController {

    private final AttendeeManager attendeeManager;

    @Autowired
    public AttendeeApiController(AttendeeManager attendeeManager) {
        this.attendeeManager = attendeeManager;
    }

    @ExceptionHandler({DataIntegrityViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<String> handleDataIntegrityException(Exception e) {
        log.warn("bad input detected", e);
        return new ResponseEntity<>("bad input parameters", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleGenericException(RuntimeException e) {
        log.error("unexpected exception", e);
        return new ResponseEntity<>("unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @PostMapping("/sponsor-scan")
    public ResponseEntity<TicketAndCheckInResult> scanBadge(@RequestBody SponsorScanRequest request, Principal principal) {
        return ResponseEntity.ok(attendeeManager.registerSponsorScan(request.eventName, request.ticketIdentifier, principal.getName()));
    }

    @PostMapping("/sponsor-scan/bulk")
    public ResponseEntity<List<TicketAndCheckInResult>> scanBadges(@RequestBody List<SponsorScanRequest> requests, Principal principal) {
        String username = principal.getName();
        return ResponseEntity.ok(requests.stream()
            .map(request -> attendeeManager.registerSponsorScan(request.eventName, request.ticketIdentifier, username))
            .collect(Collectors.toList()));
    }

    @GetMapping("/{eventKey}/sponsor-scan/mine")
    public ResponseEntity<List<SponsorAttendeeData>> getScannedBadges(@PathVariable("eventKey") String eventShortName, @RequestParam(value = "from", required = false) String from, Principal principal) {

        ZonedDateTime start = Optional.ofNullable(StringUtils.trimToNull(from))
            .map(EventUtil.JSON_DATETIME_FORMATTER::parse)
            .flatMap(d -> Wrappers.safeSupplier(() -> ZonedDateTime.of(LocalDateTime.from(d), ZoneOffset.UTC)))
            .orElse(SponsorScanRepository.DEFAULT_TIMESTAMP);
        return attendeeManager.retrieveScannedAttendees(eventShortName, principal.getName(), start).map(ResponseEntity::ok).orElse(notFound());
    }

    /**
     * API for external apps that load the ticket using its UUID. It is possible to retrieve a ticket only if <b>all</b> the following conditions are met:
     *
     * <ul>
     *     <li>An event with key {@code eventKey} exists</li>
     *     <li>The user and the event belong to the same organization</li>
     *     <li>A ticket with UUID {@code UUID} exists</li>
     * </ul>
     *
     * otherwise, an error is returned.
     *
     * @param eventShortName
     * @param uuid
     * @param principal
     * @return
     */
    @GetMapping("/{eventKey}/ticket/{UUID}")
    public Result<TicketWithAdditionalFields> getTicketDetails(@PathVariable("eventKey") String eventShortName, @PathVariable("UUID") String uuid, Principal principal) {
        return attendeeManager.retrieveTicket(eventShortName, uuid, principal.getName());
    }

    private static <T> ResponseEntity<T> notFound() {
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Getter
    public static class SponsorScanRequest {
        private final String eventName;
        private final String ticketIdentifier;

        @JsonCreator
        public SponsorScanRequest(@JsonProperty("eventName") String eventName, @JsonProperty("ticketIdentifier") String ticketIdentifier) {
            this.eventName = eventName;
            this.ticketIdentifier = ticketIdentifier;
        }
    }

}
