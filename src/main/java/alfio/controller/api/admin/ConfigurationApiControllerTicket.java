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

import alfio.controller.api.support.TicketHelper;
import alfio.job.executor.AssignTicketToSubscriberJobExecutor;
import alfio.manager.BillingDocumentManager;
import alfio.manager.EventManager;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.util.ClockProvider;
import alfio.util.RequestUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.manager.system.AdminJobExecutor.JobName.ASSIGN_TICKETS_TO_SUBSCRIBERS;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.Wrappers.optionally;
import static java.util.Objects.requireNonNullElse;

@RestController
@RequestMapping("/admin/api/configuration")
public class ConfigurationApiControllerTicket {
    private final AdminJobManager adminJobManager;
    private final EventManager eventManager;
    private final UserManager userManager;
    public ConfigurationApiControllerTicket(
                                      AdminJobManager adminJobManager,
                                      EventManager eventManager,
                                      UserManager userManager) {
        this.adminJobManager = adminJobManager;
        this.eventManager = eventManager;
        this.userManager = userManager;
    }

    @PutMapping("/generate-tickets-for-subscriptions")
    public ResponseEntity<Boolean> generateTicketsForSubscriptions(@RequestParam(value = "eventId", required = false) Integer eventId,
                                                                   @RequestParam(value = "organizationId", required = false) Integer organizationId,
                                                                   Principal principal) {
        boolean admin = RequestUtils.isAdmin(principal);
        Map<String, Object> jobMetadata = null;

        if(!admin && (organizationId == null || !userManager.isOwnerOfOrganization(principal.getName(), organizationId))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (eventId != null && organizationId != null) {
            eventManager.checkOwnership(new EventAndOrganizationId(eventId, organizationId), principal.getName(), organizationId);
            jobMetadata = Map.of(AssignTicketToSubscriberJobExecutor.EVENT_ID, eventId, AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, organizationId);
        } else if(organizationId != null) {
            jobMetadata = Map.of(AssignTicketToSubscriberJobExecutor.ORGANIZATION_ID, organizationId);
        }

        return ResponseEntity.ok(adminJobManager.scheduleExecution(ASSIGN_TICKETS_TO_SUBSCRIBERS, requireNonNullElse(jobMetadata, Map.of())));
    }
    record InstanceSettings(int descriptionMaxLength, String baseUrl) {
    }
}
