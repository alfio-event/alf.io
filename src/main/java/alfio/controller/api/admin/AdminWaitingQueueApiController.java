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

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.*;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.WaitingQueueSubscription;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.ConfigurationKeys;
import alfio.util.ClockProvider;
import alfio.util.EventUtil;
import alfio.util.ExportUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.STOP_WAITING_QUEUE_SUBSCRIPTIONS;
import static java.util.Collections.singletonList;

@RestController
@RequestMapping("/admin/api/event/{eventName}/waiting-queue")
@AllArgsConstructor
public class AdminWaitingQueueApiController {

    private final WaitingQueueManager waitingQueueManager;
    private final EventManager eventManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final ClockProvider clockProvider;
    private final AccessService accessService;

    @GetMapping("/status")
    public Map<String, Boolean> getStatusForEvent(@PathVariable("eventName") String eventName, Principal principal) {
        accessService.checkEventMembership(principal, eventName, AccessService.MEMBERSHIP_ROLES);
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(this::loadStatus)
            .orElse(Collections.emptyMap());
    }

    private Map<String, Boolean> loadStatus(Event event) {
        ZonedDateTime now = ZonedDateTime.now(clockProvider.getClock().withZone(event.getZoneId()));
        List<SaleableTicketCategory> stcList = eventManager.loadTicketCategories(event)
            .stream()
            .filter(tc -> !tc.isAccessRestricted())
            .map(tc -> new SaleableTicketCategory(tc, now, event, ticketReservationManager.countAvailableTickets(event, tc), tc.getMaxTickets(), null))
            .collect(Collectors.toList());
        boolean active = EventUtil.checkWaitingQueuePreconditions(event, stcList, configurationManager, eventStatisticsManager.noSeatsAvailable());
        boolean paused = active && configurationManager.getFor(STOP_WAITING_QUEUE_SUBSCRIPTIONS, event.getConfigurationLevel()).getValueAsBooleanOrDefault();
        Map<String, Boolean> result = new HashMap<>();
        result.put("active", active);
        result.put("paused", paused);
        return result;
    }

    @PutMapping("/status")
    public Map<String, Boolean> setStatusForEvent(@PathVariable("eventName") String eventName, @RequestBody SetStatusForm form, Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                configurationManager.saveAllEventConfiguration(event.getId(), event.getOrganizationId(),
                    singletonList(new ConfigurationModification(null, ConfigurationKeys.STOP_WAITING_QUEUE_SUBSCRIPTIONS.name(), String.valueOf(form.status))),
                    principal.getName());
                return loadStatus(event);
            }).orElse(Collections.emptyMap());
    }

    @GetMapping("/count")
    public Integer countWaitingPeople(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) {
        accessService.checkEventMembership(principal, eventName, AccessService.MEMBERSHIP_ROLES);
        Optional<Integer> count = eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(e -> waitingQueueManager.countSubscribers(e.getId()));
        if(count.isPresent()) {
            return count.get();
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return 0;
    }

    @GetMapping("/load")
    public List<WaitingQueueSubscription> loadAllSubscriptions(@PathVariable("eventName") String eventName, Principal principal, HttpServletResponse response) {
        accessService.checkEventOwnership(principal, eventName);
        Optional<List<WaitingQueueSubscription>> count = eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(e -> waitingQueueManager.loadAllSubscriptionsForEvent(e.getId()));
        if(count.isPresent()) {
            return count.get();
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return Collections.emptyList();
    }

    @GetMapping("/download")
    public void downloadAllSubscriptions(@PathVariable("eventName") String eventName,
                                         @RequestParam(name = "format", defaultValue = "excel") String format,
                                         Principal principal, HttpServletResponse response) throws IOException {
        accessService.checkEventOwnership(principal, eventName);
        var event = eventManager.getSingleEvent(eventName, principal.getName());
        var found = waitingQueueManager.loadAllSubscriptionsForEvent(event.getId());

        var header = new String[] {"Type", "Firstname", "Lastname", "Email", "Language", "Status", "Date"};
        var lines = convertSubscriptions(found, event);
        if ("excel".equals(format)) {
            ExportUtils.exportExcel(event.getShortName() + "-waiting-queue.xlsx", "waiting-queue",
                header,
                lines, response);
        } else {
            ExportUtils.exportCsv(event.getShortName() + "-waiting-queue.csv", header, lines, response);
        }
    }

    private static Stream<String[]> convertSubscriptions(List<WaitingQueueSubscription> l, Event event) {
        return l.stream().map(s -> new String[] {s.getSubscriptionType().toString(),
            s.getFirstName(), s.getLastName(), s.getEmailAddress(),
            s.getLocale().toLanguageTag(), s.getStatus().name(),
            s.getCreation().withZoneSameInstant(event.getZoneId()).toString()});
    }

    @DeleteMapping("/subscriber/{subscriberId}")
    public ResponseEntity<Map<String, Object>> removeSubscriber(@PathVariable("eventName") String eventName,
                                                                @PathVariable("subscriberId") int subscriberId,
                                                                Principal principal) {
        return performStatusModification(eventName, subscriberId, principal, WaitingQueueSubscription.Status.CANCELLED, WaitingQueueSubscription.Status.WAITING);
    }

    @PutMapping("/subscriber/{subscriberId}/restore")
    public ResponseEntity<Map<String, Object>> restoreSubscriber(@PathVariable("eventName") String eventName,
                                                                 @PathVariable("subscriberId") int subscriberId,
                                                                 Principal principal) {
        return performStatusModification(eventName, subscriberId, principal, WaitingQueueSubscription.Status.WAITING, WaitingQueueSubscription.Status.CANCELLED);
    }

    private ResponseEntity<Map<String, Object>> performStatusModification(String eventName, int subscriberId,
                                                                          Principal principal, WaitingQueueSubscription.Status newStatus,
                                                                          WaitingQueueSubscription.Status currentStatus) {
        var eventAndOrgId = accessService.checkWaitingQueueSubscriberInEvent(principal, subscriberId, eventName);
        return waitingQueueManager.updateSubscriptionStatus(subscriberId, newStatus, currentStatus)
            .map(result -> {
                Map<String, Object> out = new HashMap<>();
                out.put("modified", result);
                out.put("list", waitingQueueManager.loadAllSubscriptionsForEvent(eventAndOrgId.getId()));
                return out;
            })
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.BAD_REQUEST));
    }

    @Data
    private static class SetStatusForm {
        private boolean status;
    }


}
