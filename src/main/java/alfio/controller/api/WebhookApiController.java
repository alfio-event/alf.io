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

import alfio.manager.MollieManager;
import alfio.manager.TicketReservationManager;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class WebhookApiController {


    private final MollieManager mollieManager;
    private final TicketReservationManager ticketReservationManager;

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/webhook/mollie", method = RequestMethod.POST)
    public void handleMollie(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId) throws Exception {
        // mollieManager.handleWebhook(eventName, reservationId, null);
        // call ticketReservationManager.confirm... if handlewebhoook return status paid
    }
}
