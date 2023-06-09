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

import alfio.controller.api.support.PageAndContent;
import alfio.manager.AccessService;
import alfio.manager.NotificationManager;
import alfio.manager.PurchaseContextManager;
import alfio.model.EmailMessage;
import alfio.model.LightweightMailMessage;
import alfio.model.PurchaseContext;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin/api/{purchaseContextType}/{publicIdentifier}/email")
public class EmailMessageApiController {

    private final NotificationManager notificationManager;
    private final PurchaseContextManager purchaseContextManager;
    private final AccessService accessService;

    @GetMapping("/")
    public PageAndContent<List<LightweightEmailMessage>> loadEmailMessages(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                                           @PathVariable("publicIdentifier") String publicIdentifier,
                                                                           @RequestParam(value = "page", required = false) Integer page,
                                                                           @RequestParam(value = "search", required = false) String search,
                                                                           Principal principal) {
        accessService.checkPurchaseContextOwnership(principal, purchaseContextType, publicIdentifier);
        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        ZoneId zoneId = purchaseContext.getZoneId();
        Pair<Integer, List<LightweightMailMessage>> found = notificationManager.loadAllMessagesForPurchaseContext(purchaseContext, page, search);
        return new PageAndContent<>(found.getRight().stream()
            .map(m -> new LightweightEmailMessage(m, zoneId, true))
            .collect(Collectors.toList()), found.getLeft());
    }

    @GetMapping("/{messageId}")
    public LightweightEmailMessage loadEmailMessage(@PathVariable("purchaseContextType") PurchaseContext.PurchaseContextType purchaseContextType,
                                                    @PathVariable("publicIdentifier") String publicIdentifier,
                                                    @PathVariable("messageId") int messageId,
                                                    Principal principal) {
        var purchaseContext = purchaseContextManager.findBy(purchaseContextType, publicIdentifier).orElseThrow();
        accessService.checkOrganizationOwnership(principal, purchaseContext.getOrganizationId());
        return notificationManager.loadSingleMessageForPurchaseContext(purchaseContext, messageId).map(m -> new LightweightEmailMessage(m, purchaseContext.getZoneId(), false)).orElseThrow(IllegalArgumentException::new);
    }

    @AllArgsConstructor
    private static final class LightweightEmailMessage {
        @Delegate(excludes = LightweightExclusions.class)
        private final EmailMessage src;
        private final ZoneId eventZoneId;
        private final boolean list;

        public String getAttachments() {
            return null;
        }

        public ZonedDateTime getRequestTimestamp() {
            return src.getRequestTimestamp().withZoneSameInstant(eventZoneId);
        }

        public ZonedDateTime getSentTimestamp() {
            return Optional.ofNullable(src.getSentTimestamp()).map(t -> t.withZoneSameInstant(eventZoneId)).orElse(null);
        }

        public String getMessage() {
            if(list) {
                return StringUtils.abbreviate(src.getMessage(), 128);//the most important information are stored in the first ~100 chars
            }
            return src.getMessage();
        }

    }

    private interface LightweightExclusions {
        String getAttachments();
        ZonedDateTime getRequestTimestamp();
        ZonedDateTime getSentTimestamp();
        String getMessage();
    }
}
