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

import alfio.manager.EventManager;
import alfio.manager.WaitingQueueManager;
import alfio.model.WaitingQueueSubscription;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static alfio.util.OptionalWrapper.optionally;

@RestController
@RequestMapping("/admin/api/event/{eventName}/waiting-queue")
public class AdminWaitingQueueApiController {

    private final WaitingQueueManager waitingQueueManager;
    private final EventManager eventManager;

    @Autowired
    public AdminWaitingQueueApiController(WaitingQueueManager waitingQueueManager,
                                          EventManager eventManager) {
        this.waitingQueueManager = waitingQueueManager;
        this.eventManager = eventManager;
    }

    @RequestMapping(value = "/count", method = RequestMethod.GET)
    public Integer countWaitingPeople(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) {
        Optional<Integer> count = optionally(() -> eventManager.getSingleEvent(eventName, principal.getName())).map(e -> waitingQueueManager.countSubscribers(e.getId()));
        if(count.isPresent()) {
            return count.get();
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return 0;
    }

    @RequestMapping(value = "/load", method = RequestMethod.GET)
    public List<WaitingQueueSubscription> loadAllSubscriptions(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) {
        Optional<List<WaitingQueueSubscription>> count = optionally(() -> eventManager.getSingleEvent(eventName, principal.getName())).map(e -> waitingQueueManager.loadAllSubscriptionsForEvent(e.getId()));
        if(count.isPresent()) {
            return count.get();
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return Collections.emptyList();
    }

}
