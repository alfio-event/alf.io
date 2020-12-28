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
package alfio.controller.api.v2.user;

import alfio.controller.api.v2.model.BasicSubscriptionInfo;
import alfio.manager.SubscriptionManager;
import alfio.util.ClockProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/public/")
public class SubscriptionsApiController {

    private final SubscriptionManager subscriptionManager;

    public SubscriptionsApiController(SubscriptionManager subscriptionManager) {
        this.subscriptionManager = subscriptionManager;
    }

    @GetMapping("subscriptions")
    public ResponseEntity<List<BasicSubscriptionInfo>> listSubscriptions(/* TODO search by: organizer, tag, subscription */) {
        var now = ZonedDateTime.now(ClockProvider.clock());
        var activeSubscriptions = subscriptionManager.getActivePublicSubscriptionsDescriptor(now)
            .stream()
            .map(s -> new BasicSubscriptionInfo(s.getId()))
            .collect(Collectors.toList());
        return ResponseEntity.ok(activeSubscriptions);
    }
}
