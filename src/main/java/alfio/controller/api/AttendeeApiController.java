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
package alfio.controller.api;

import alfio.manager.AttendeeManager;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

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
        return new ResponseEntity<>("the requested resource already exists", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleGenericException(RuntimeException e) {
        log.error("unexpected exception", e);
        return new ResponseEntity<>("unexpected error", HttpStatus.INTERNAL_SERVER_ERROR);
    }


    @RequestMapping(value = "/sponsor-scan", method = RequestMethod.POST)
    public ResponseEntity<ZonedDateTime> scanBadge(@RequestParam("eventId") int eventId, @RequestParam("ticketIdentifier") String ticketIdentifier) {
        return attendeeManager.registerSponsorScan(eventId, ticketIdentifier).map(z -> new ResponseEntity<>(z, HttpStatus.CONFLICT)).orElseGet(() -> new ResponseEntity<>(HttpStatus.OK));
    }

}
