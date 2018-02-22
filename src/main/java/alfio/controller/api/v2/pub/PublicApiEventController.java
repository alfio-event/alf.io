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

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v2/public/event/")
public class PublicApiEventController {

    @RequestMapping(method = RequestMethod.GET, path = "/", produces = "application/json")
    @ApiOperation(value = "Return a list of available events")
    public List<Void> getEvents(@RequestParam("lang") String lang) {
        return Collections.emptyList();
    }


    @RequestMapping(method = RequestMethod.GET, path = "/{shortName}", produces = "application/json")
    @ApiOperation(value = "Return an event")
    public Void getEvent(@PathVariable("shortName") String shortName,
                         @RequestParam("lang") String lang,
                         @RequestParam(name = "code", required = false) String discountCode) {
        return null;
    }
}
