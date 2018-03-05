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

import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/public/ticket/")
public class PublicApiTicketController {

    @GetMapping("/{ticketUuid}")
    @ApiOperation("Get a ticket")
    public Void getTicket(@PathVariable("ticketUuid") String ticketUuid) {
        return null;
    }

    @PostMapping("/{ticketUuid}")
    @ApiOperation("Get a ticket")
    public Void updateTicket(@PathVariable("ticketUuid") String ticketUuid, @RequestBody Void ticket) {
        return null;
    }

    @PostMapping("/{ticketUuid}/send")
    @ApiOperation("Sends ticket via email")
    public boolean sendTicketViaEmail(@PathVariable("ticketUuid") String ticketUuid) {
        return false;
    }

    @GetMapping("/{ticketUuid}/download")
    @ApiOperation("Download ticket PDF")
    public void downloadTicketPDF(@PathVariable("ticketUuid") String ticketUuid) {
    }

    @DeleteMapping("/{ticketUuid}")
    @ApiOperation("Sends ticket via email")
    public boolean releaseTicket(@PathVariable("ticketUuid") String ticketUuid) {
        return false;
    }
}
