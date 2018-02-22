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

    @GetMapping("/{uuid}")
    @ApiOperation("Get a ticket")
    public Void getTicket(@PathVariable("uuid") String uuid) {
        return null;
    }

    @PostMapping("/{uuid}")
    @ApiOperation("Get a ticket")
    public Void updateTicket(@PathVariable("uuid") String uuid, @RequestBody Void ticket) {
        return null;
    }

    @PostMapping("/{uuid}/send")
    @ApiOperation("Sends ticket via email")
    public boolean sendTicketViaEmail(@PathVariable("uuid") String uuid) {
        return false;
    }

    @GetMapping("/{uuid}/download")
    @ApiOperation("Download ticket PDF")
    public void downloadTicketPDF(@PathVariable("uuid") String uuid) {
    }

    @DeleteMapping("/{uuid}")
    @ApiOperation("Sends ticket via email")
    public boolean releaseTicket(@PathVariable("uuid") String uuid) {
        return false;
    }
}
