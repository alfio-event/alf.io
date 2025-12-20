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

import alfio.manager.AccessService;
import alfio.manager.CheckInManager;
import alfio.manager.EventManager;
import alfio.manager.support.*;
import alfio.manager.system.ConfigurationManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.FullTicketInfo;
import alfio.model.checkin.AttendeeSearchResults;
import alfio.model.system.ConfigurationKeys;
import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.manager.AccessService.MEMBERSHIP_ROLES;
import static alfio.util.MiscUtils.removeTabsAndNewlines;
import static alfio.util.Wrappers.optionally;

@RestController
@RequestMapping("/admin/api")
public class CheckInApiController {

    private static final Logger log = LoggerFactory.getLogger(CheckInApiController.class);
    private static final String ALFIO_TIMESTAMP_HEADER = "Alfio-TIME";
    private final CheckInManager checkInManager;
    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;
    private final AccessService accessService;

    public CheckInApiController(CheckInManager checkInManager, EventManager eventManager, ConfigurationManager configurationManager, AccessService accessService) {
        this.checkInManager = checkInManager;
        this.eventManager = eventManager;
        this.configurationManager = configurationManager;
        this.accessService = accessService;
    }

    @Data
    public static class TicketCode {
        private String code;
    }

    @Data
    public static class TicketIdentifierCode {
        private String identifier;
        private String code;
    }
    
    @GetMapping("/check-in/{eventId}/ticket/{ticketIdentifier}")
    public TicketAndCheckInResult findTicketWithUUID(@PathVariable int eventId, @PathVariable String ticketIdentifier, @RequestParam("qrCode") String qrCode, Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventId, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return checkInManager.evaluateTicketStatus(eventId, ticketIdentifier, Optional.ofNullable(qrCode));
    }

    @GetMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}")
    public TicketAndCheckInResult findTicketWithUUID(@PathVariable String eventName, @PathVariable String ticketIdentifier, @RequestParam("qrCode") String qrCode, Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return checkInManager.evaluateTicketStatus(eventName, ticketIdentifier, Optional.ofNullable(qrCode));
    }

    @GetMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}/status")
    public TicketCheckInStatusResult getTicketStatus(@PathVariable String eventName, @PathVariable String ticketIdentifier, Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return checkInManager.retrieveTicketStatus(ticketIdentifier);
    }

    @PostMapping("/check-in/{eventId}/ticket/{ticketIdentifier}")
    public TicketAndCheckInResult checkIn(@PathVariable int eventId,
                                          @PathVariable String ticketIdentifier,
                                          @RequestBody TicketCode ticketCode,
                                          Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventId, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return checkInManager.checkIn(eventId, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), principal.getName());
    }

    @PostMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}")
    public TicketAndCheckInResult checkIn(@PathVariable String eventName,
                                          @PathVariable String ticketIdentifier,
                                          @RequestBody TicketCode ticketCode,
                                          @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                          Principal principal) {
        try {
            accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
            String username = principal.getName();
            String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
            return checkInManager.checkIn(eventName, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), username, auditUser);
        } catch (AccessDeniedException e) {
            // mobile app doesn't know how to handle 403 results, so we return a "TICKET_NOT_FOUND" code instead.
            return new TicketAndCheckInResult(null, new DefaultCheckInResult(CheckInStatus.TICKET_NOT_FOUND, "ticket not found"));
        }
    }

    @PostMapping("/check-in/event/{eventName}/bulk")
    public Map<String, TicketAndCheckInResult> bulkCheckIn(@PathVariable String eventName,
                                                           @RequestBody List<TicketIdentifierCode> ticketIdentifierCodes,
                                                           @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                                           @RequestParam(value = "forceCheckInPaymentOnSite", required = false, defaultValue = "false") boolean forceCheckInPaymentOnSite,
                                                           Principal principal) {
        accessService.checkEventMembership(principal, eventName, AccessService.CHECKIN_ROLES);
        String username = principal.getName();
        String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
        return ticketIdentifierCodes.stream()
            .distinct()
            .map(t -> {
                TicketAndCheckInResult res = checkInManager.checkIn(eventName, t.getIdentifier(),
                    Optional.ofNullable(t.getCode()),
                    username, auditUser, forceCheckInPaymentOnSite);
                return Pair.of(t.identifier, res);
            })
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @PostMapping("/check-in/{eventId}/ticket/{ticketIdentifier}/manual-check-in")
    public boolean manualCheckIn(@PathVariable int eventId,
                                 @PathVariable String ticketIdentifier,
                                 Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventId, ticketIdentifier, AccessService.CHECKIN_ROLES);
        log.warn("for event id : {} and ticket : {}, a manual check in has been done by {}", eventId, ticketIdentifier, principal.getName());
        return checkInManager.manualCheckIn(eventId, ticketIdentifier, principal.getName());
    }

    @PostMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}/manual-check-in")
    public ResponseEntity<Boolean> manualCheckIn(@PathVariable String eventName,
                                 @PathVariable String ticketIdentifier,
                                 Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return ResponseEntity.of(eventManager.getOptionalEventAndOrganizationIdByName(eventName, principal.getName())
            .map(ev -> manualCheckIn(ev.getId(), ticketIdentifier, principal)));
    }

    @PostMapping("/check-in/{eventId}/ticket/{ticketIdentifier}/revert-check-in")
    public boolean revertCheckIn(@PathVariable int eventId,
                                 @PathVariable String ticketIdentifier,
                                 Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventId, ticketIdentifier, AccessService.CHECKIN_ROLES);
        log.warn("for event id : {} and ticket : {}, a revert of the check in has been done by {}", eventId, removeTabsAndNewlines(ticketIdentifier), principal.getName());
        return checkInManager.revertCheckIn(eventId, ticketIdentifier, principal.getName());
    }

    @PostMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}/revert-check-in")
    public ResponseEntity<Boolean> revertCheckIn(@PathVariable String eventName,
                                 @PathVariable String ticketIdentifier,
                                 Principal principal) {
        var eventAndOrgId = accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return ResponseEntity.ok(revertCheckIn(eventAndOrgId.getId(), ticketIdentifier, principal));
    }

    @PostMapping("/check-in/event/{eventName}/ticket/{ticketIdentifier}/confirm-on-site-payment")
    public TicketAndCheckInResult confirmOnSitePayment(@PathVariable String eventName,
                                                       @PathVariable String ticketIdentifier,
                                                       @RequestBody TicketCode ticketCode,
                                                       @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                                       Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventName, ticketIdentifier, AccessService.CHECKIN_ROLES);
        String username = principal.getName();
        String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
        return checkInManager.confirmOnSitePayment(eventName, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), username, auditUser);
    }

    @GetMapping("/check-in/event/{eventName}/statistics")
    public CheckInStatistics getStatistics(@PathVariable String eventName,
                                           @RequestParam(name = "categoryId", required = false) List<Integer> categories,
                                           Principal principal) {
        accessService.checkEventMembership(principal, eventName, AccessService.CHECKIN_ROLES);
        return checkInManager.getStatistics(eventName, categories, principal.getName());
    }
    
    @PostMapping("/check-in/{eventId}/ticket/{ticketIdentifier}/confirm-on-site-payment")
    public OnSitePaymentConfirmation confirmOnSitePayment(@PathVariable int eventId, @PathVariable String ticketIdentifier, Principal principal) {
        accessService.checkEventTicketIdentifierMembership(principal, eventId, ticketIdentifier, AccessService.CHECKIN_ROLES);
        return checkInManager.confirmOnSitePayment(ticketIdentifier)
            .map(s -> new OnSitePaymentConfirmation(true, "ok"))
            .orElseGet(() -> new OnSitePaymentConfirmation(false, "Ticket with uuid " + ticketIdentifier + " not found"));
    }

    @GetMapping("/check-in/{eventId}/ticket-identifiers")
    public List<Integer> findAllIdentifiersForAdminCheckIn(@PathVariable int eventId,
                                               @RequestParam(value = "changedSince", required = false) Long changedSince,
                                               HttpServletResponse response,
                                               Principal principal) {
        accessService.checkEventMembership(principal, eventId, AccessService.CHECKIN_ROLES);
        response.setHeader(ALFIO_TIMESTAMP_HEADER, Long.toString(new Date().getTime()));
        return checkInManager.getAttendeesIdentifiers(eventId, changedSince == null ? new Date(0) : new Date(changedSince), principal.getName());
    }

    @GetMapping("/check-in/event/{publicIdentifier}/attendees")
    public ResponseEntity<AttendeeSearchResults> searchAttendees(@PathVariable String publicIdentifier,
                                                                 @RequestParam(value = "query", required = false) String query,
                                                                 @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                                                                 Principal principal) {
        accessService.checkEventMembership(principal, publicIdentifier, MEMBERSHIP_ROLES);
        if (StringUtils.isBlank(query) || StringUtils.isBlank(publicIdentifier)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }

        return ResponseEntity.of(eventManager.getOptionalByName(publicIdentifier, principal.getName())
            .map(event -> checkInManager.searchAttendees(event, query, page, principal)));

    }

    @PostMapping("/check-in/{eventId}/tickets")
    public List<FullTicketInfo> findAllTicketsForAdminCheckIn(@PathVariable int eventId,
                                                              @RequestBody List<Integer> ids,
                                                              Principal principal) {
        accessService.checkEventMembership(principal, eventId, AccessService.CHECKIN_ROLES);
        validateIdList(ids);
        return checkInManager.getAttendeesInformation(eventId, ids, principal.getName());
    }

    @GetMapping("/check-in/{eventName}/label-layout")
    public ResponseEntity<LabelLayout> getLabelLayoutForEvent(@PathVariable String eventName, Principal principal) {
        var event = accessService.canAccessEvent(principal, eventName);
        if (checkInManager.isOfflineCheckInEnabled().test(event)) {
            return parseLabelLayout(event);
        } else {
            return new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED);
        }
    }

    @GetMapping("/check-in/{eventName}/offline-identifiers")
    public List<Integer> getOfflineIdentifiers(@PathVariable String eventName,
                                              @RequestParam(value = "changedSince", required = false) Long changedSince,
                                              HttpServletResponse resp,
                                              Principal principal) {
        var event = accessService.checkEventMembership(principal, eventName, AccessService.CHECKIN_ROLES);
        Date since = changedSince == null ? new Date(0) : DateUtils.addSeconds(new Date(changedSince), -1);
        List<Integer> ids;
        boolean offlineEnabled = checkInManager.isOfflineCheckInEnabled().test(event);
        if (offlineEnabled) {
            ids = checkInManager.getAttendeesIdentifiers(event, since, principal.getName());
        } else {
            ids = List.of();
        }
        resp.setHeader(ALFIO_TIMESTAMP_HEADER, offlineEnabled ? Long.toString(new Date().getTime()) : "0");
        return ids;
    }

    @PostMapping("/check-in/{eventName}/offline")
    public Map<String, String> getOfflineEncryptedInfo(@PathVariable String eventName,
                                                       @RequestParam(value = "additionalField", required = false) List<String> additionalFields,
                                                       @RequestBody List<Integer> ids,
                                                       Principal principal) {
        accessService.checkEventMembership(principal, eventName, AccessService.CHECKIN_ROLES);

        validateIdList(ids);
        return eventManager.getOptionalByName(eventName, principal.getName())
            .map(event -> {
                Set<String> addFields = loadLabelLayout(event)
                    .map(layout -> {
                        Set<String> union = new HashSet<>(layout.content.thirdRow);
                        union.addAll(layout.content.additionalRows);
                        union.addAll(layout.qrCode.additionalInfo);
                        if(additionalFields != null && !additionalFields.isEmpty()) {
                            union.addAll(additionalFields);
                        }
                        return union;
                    })
                    .orElseGet(() -> {
                        if(additionalFields != null && !additionalFields.isEmpty()) {
                            return new HashSet<>(additionalFields);
                        }
                        return Collections.singleton("company");
                    });
                return checkInManager.getEncryptedAttendeesInformation(event, addFields, ids);
            }).orElse(Collections.emptyMap());
    }

    private static void validateIdList(@RequestBody List<Integer> ids) {
        Validate.isTrue(ids!= null && !ids.isEmpty());
        Validate.isTrue(ids.size() <= 200, "Cannot ask more than 200 ids");
    }

    private ResponseEntity<LabelLayout> parseLabelLayout(EventAndOrganizationId event) {
        return loadLabelLayout(event)
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    private Optional<LabelLayout> loadLabelLayout(EventAndOrganizationId event) {
        return configurationManager.getFor(ConfigurationKeys.LABEL_LAYOUT, event.getConfigurationLevel()).getValue()
            .flatMap(str -> optionally(() -> Json.fromJson(str, LabelLayout.class)));
    }

    @Data
    public static class OnSitePaymentConfirmation {
        private final boolean status;
        private final String message;
    }

    @Getter
    public static class LabelLayout {

        private final QRCode qrCode;
        private final Content content;
        private final General general;
        private final String mediaName;

        @JsonCreator
        private LabelLayout(@JsonProperty("qrCode") QRCode qrCode,
                            @JsonProperty("content") Content content,
                            @JsonProperty("general") General general,
                            @JsonProperty("mediaName") String mediaName) {
            this.qrCode = qrCode;
            this.content = content;
            this.general = general;
            this.mediaName = mediaName;
        }

        @Getter
        private static class QRCode {
            private final List<String> additionalInfo;
            private final String infoSeparator;

            @JsonCreator
            private QRCode(@JsonProperty("additionalInfo") List<String> additionalInfo,
                           @JsonProperty("infoSeparator") String infoSeparator) {
                this.additionalInfo = additionalInfo;
                this.infoSeparator = infoSeparator;
            }
        }

        @Getter
        private static class Content {

            private final String firstRow;
            private final String secondRow;
            private final List<String> thirdRow;
            private final List<String> additionalRows;
            private final Boolean checkbox;

            @JsonCreator
            private Content(@JsonProperty("firstRow") String firstRow,
                            @JsonProperty("secondRow") String secondRow,
                            @JsonProperty("thirdRow") List<String> thirdRow,
                            @JsonProperty("additionalRows") List<String> additionalRows,
                            @JsonProperty("checkbox") Boolean checkbox) {
                this.firstRow = firstRow;
                this.secondRow = secondRow;
                this.thirdRow = thirdRow != null ? thirdRow : List.of();
                this.additionalRows = additionalRows != null ? additionalRows : List.of();
                this.checkbox = checkbox;
            }
        }

        @Getter
        private static class General {
            private final boolean printPartialID;

            @JsonCreator
            private General(@JsonProperty("printPartialID") boolean printPartialID) {
                this.printPartialID = printPartialID;
            }
        }
    }

}
