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
public class ConfigurationApiControllerInvoice {

    private final BillingDocumentManager billingDocumentManager;
    private final AdminJobManager adminJobManager;
    private final EventManager eventManager;
    private final ClockProvider clockProvider;

    public ConfigurationApiControllerInvoice(BillingDocumentManager billingDocumentManager,
                                      AdminJobManager adminJobManager,
                                      EventManager eventManager,
                                      ClockProvider clockProvider,) {
        this.billingDocumentManager = billingDocumentManager;
        this.adminJobManager = adminJobManager;
        this.eventManager = eventManager;
        this.clockProvider = clockProvider;
    }

    @GetMapping(value = "/event/{eventId}/invoice-first-date")
    public ResponseEntity<ZonedDateTime> getFirstInvoiceDate(@PathVariable("eventId") Integer eventId, Principal principal) {
        return ResponseEntity.of(optionally(() -> eventManager.getSingleEventById(eventId, principal.getName()))
            .map(event -> billingDocumentManager.findFirstInvoiceDate(event.getId()).orElseGet(() -> ZonedDateTime.now(clockProvider.getClock().withZone(event.getZoneId())))));
    }

    @GetMapping(value = "/event/{eventId}/matching-invoices")
    public ResponseEntity<List<Integer>> getMatchingInvoicesForEvent(@PathVariable("eventId") Integer eventId,
                                                                     @RequestParam("from") long fromInstant,
                                                                     @RequestParam("to") long toInstant,
                                                                     Principal principal) {
        var eventOptional = optionally(() -> eventManager.getSingleEventById(eventId, principal.getName()));
        if(eventOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var zoneId = eventOptional.get().getZoneId();
        var from = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromInstant), zoneId);
        var to = ZonedDateTime.ofInstant(Instant.ofEpochMilli(toInstant), zoneId);
        if(from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(billingDocumentManager.findMatchingInvoiceIds(eventId, from, to));
    }

    @PostMapping(value = "/event/{eventId}/regenerate-invoices")
    public ResponseEntity<Boolean> regenerateInvoices(@PathVariable("eventId") Integer eventId,
                                                      @RequestBody List<Long> documentIds,
                                                      Principal principal) {
        if(!eventManager.eventExistsById(eventId) || documentIds.isEmpty()) {
            // implicit check done by the Row Level Security
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(adminJobManager.scheduleExecution(AdminJobExecutor.JobName.REGENERATE_INVOICES, Map.of(
            "username", principal.getName(),
            "eventId", eventId,
            "ids", documentIds.stream().map(String::valueOf).collect(Collectors.joining(","))
        )));
    }
}
