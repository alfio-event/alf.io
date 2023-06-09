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
package alfio.controller.api.admin;

import alfio.manager.AccessService;
import alfio.manager.AdminReservationRequestManager;
import alfio.model.AdminReservationRequestStats;
import alfio.model.modification.AdminReservationModification;
import alfio.model.result.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RequestMapping("/admin/api/event/{eventName}/attendees/import")
@RestController
@AllArgsConstructor
public class AttendeeBulkImportApiController {

    private final AdminReservationRequestManager requestManager;
    private final AccessService accessService;

    @PostMapping("")
    public Result<String> createReservations(@PathVariable("eventName") String eventName,
                                             @RequestBody AdminReservationModification body,
                                             @RequestParam(name="oneReservationPerAttendee", defaultValue = "false", required = false) boolean oneReservationPerAttendee,
                                             Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        return requestManager.scheduleReservations(eventName, body, !oneReservationPerAttendee, principal.getName());
    }

    @GetMapping("/{requestId}/status")
    public Result<AdminReservationRequestStats> getRequestsStatus(@PathVariable("eventName") String eventName,
                                                                  @PathVariable("requestId") String requestId,
                                                                  Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        return requestManager.getRequestStatus(requestId, eventName, principal.getName());
    }

}

