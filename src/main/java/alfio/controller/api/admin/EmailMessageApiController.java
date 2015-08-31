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
import alfio.manager.NotificationManager;
import alfio.model.EmailMessage;
import alfio.model.Event;
import lombok.Data;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/events/{eventName}/email")
public class EmailMessageApiController {

    private final NotificationManager notificationManager;
    private final EventManager eventManager;

    @Autowired
    public EmailMessageApiController(NotificationManager notificationManager, EventManager eventManager) {
        this.notificationManager = notificationManager;
        this.eventManager = eventManager;
    }

    @RequestMapping("/")
    public List<LightweightEmailMessage> loadEmailMessages(@PathVariable("eventName") String eventName, Principal principal) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        ZoneId zoneId = event.getZoneId();
        return notificationManager.loadAllMessagesForEvent(event.getId()).stream()
            .map(m -> new LightweightEmailMessage(m, zoneId))
            .collect(Collectors.toList());
    }

    @RequestMapping("/{messageId}")
    public LightweightEmailMessage loadEmailMessage(@PathVariable("eventName") String eventName, @PathVariable("messageId") int messageId, Principal principal) {
        Event event = eventManager.getSingleEvent(eventName, principal.getName());
        return notificationManager.loadSingleMessageForEvent(event.getId(), messageId).map(m -> new LightweightEmailMessage(m, event.getZoneId())).orElseThrow(IllegalArgumentException::new);
    }

    @Data
    public static final class LightweightEmailMessage {
        @Delegate(excludes = DelegateExclusions.class)
        private final EmailMessage src;
        private final ZoneId eventZoneId;

        public String getAttachments() {
            return null;
        }

        public ZonedDateTime getRequestTimestamp() {
            return src.getRequestTimestamp().withZoneSameInstant(eventZoneId);
        }

        public ZonedDateTime getSentTimestamp() {
            return Optional.ofNullable(src.getSentTimestamp()).map(t -> t.withZoneSameInstant(eventZoneId)).orElse(null);
        }

        private ZonedDateTime safeConvertTimestamp(ZonedDateTime timestamp) {
            return Optional.ofNullable(timestamp).map(t -> t.withZoneSameInstant(eventZoneId)).orElse(null);
        }
    }

    public interface DelegateExclusions {
        String getAttachments();
        ZonedDateTime getRequestTimestamp();
        ZonedDateTime getSentTimestamp();
    }
}
