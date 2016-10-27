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

import alfio.manager.AdminReservationManager;
import alfio.model.Ticket;
import alfio.model.TicketReservation;
import alfio.model.modification.AdminReservationModification;
import alfio.model.result.Result;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RequestMapping("/admin/api/reservation")
@RestController
public class AdminReservationApiController {
    private final AdminReservationManager adminReservationManager;

    @Autowired
    public AdminReservationApiController(AdminReservationManager adminReservationManager) {
        this.adminReservationManager = adminReservationManager;
    }

    @RequestMapping(value = "/event/{eventName}/new", method = RequestMethod.POST)
    public Result<Pair<TicketReservation, List<Ticket>>> createNew(@PathVariable("eventName") String eventName, @RequestBody AdminReservationModification reservation, Principal principal) {
        return adminReservationManager.createReservation(reservation, eventName, principal.getName());
    }
}
