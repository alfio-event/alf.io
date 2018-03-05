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
package alfio.controller.api.v2.pub;

import alfio.model.result.Result;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v2/public/reservation/")
public class PublicApiReservationController {

    @PostMapping("/")
    @ApiOperation(value = "Creates a new Reservation")
    public Result<TicketReservation> createReservation() {
        return null;
    }

    @GetMapping("/{reservationUuid}")
    @ApiOperation(value = "Retrieves a reservation")
    public Result<TicketReservation> getReservation(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {
        return null;
    }

    @PostMapping("/{reservationUuid}")
    @ApiOperation(value = "Updates a Reservation")
    public Result<TicketReservation> updateReservation(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {
        return null;
    }

    @PostMapping("/{reservationUuid}/reverse-charge")
    @ApiOperation(value = "Validate and request Reverse Charge for EU-Based events")
    public Result<Boolean> requestReverseCharge(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {
        return null;
    }

    @PostMapping("/{reservationUuid}/confirm")
    @ApiOperation(value = "Confirm a Reservation")
    public Result<TicketReservation> confirm(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {
        return null;
    }

    @PostMapping("/{reservationUuid}/send")
    @ApiOperation(value = "Send the confirmation email")
    public Result<TicketReservation> sendEmail(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {
        return null;
    }

    @GetMapping("/{reservationUuid}/download")
    @ApiOperation(value = "Download Receipt or Invoice", produces = "application/pdf")
    public void downloadReceiptOrInvoice(@PathVariable("reservationUuid") String reservationUuid, Principal principal) {

    }

    private class TicketReservation {}
}
