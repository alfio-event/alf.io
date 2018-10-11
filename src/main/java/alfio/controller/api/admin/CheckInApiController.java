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

import alfio.manager.CheckInManager;
import alfio.manager.EventManager;
import alfio.manager.support.CheckInStatistics;
import alfio.manager.support.TicketAndCheckInResult;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.FullTicketInfo;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.util.OptionalWrapper.optionally;
import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@Log4j2
@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class CheckInApiController {

    private static final String ALFIO_TIMESTAMP_HEADER = "Alfio-TIME";
    private final CheckInManager checkInManager;
    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;

    @Data
    public static class TicketCode {
        private String code;
    }

    @Data
    public static class TicketIdentifierCode {
        private String identifier;
        private String code;
    }
    
    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}", method = GET)
    public TicketAndCheckInResult findTicketWithUUID(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier, @RequestParam("qrCode") String qrCode) {
        return checkInManager.evaluateTicketStatus(eventId, ticketIdentifier, Optional.ofNullable(qrCode));
    }

    @RequestMapping(value = "/check-in/event/{eventName}/ticket/{ticketIdentifier}", method = GET)
    public TicketAndCheckInResult findTicketWithUUID(@PathVariable("eventName") String eventName, @PathVariable("ticketIdentifier") String ticketIdentifier, @RequestParam("qrCode") String qrCode) {
        return checkInManager.evaluateTicketStatus(eventName, ticketIdentifier, Optional.ofNullable(qrCode));
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}", method = POST)
    public TicketAndCheckInResult checkIn(@PathVariable("eventId") int eventId,
                                          @PathVariable("ticketIdentifier") String ticketIdentifier,
                                          @RequestBody TicketCode ticketCode,
                                          Principal principal) {
        return checkInManager.checkIn(eventId, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), principal.getName());
    }

    @RequestMapping(value = "/check-in/event/{eventName}/ticket/{ticketIdentifier}", method = POST)
    public TicketAndCheckInResult checkIn(@PathVariable("eventName") String eventName,
                                          @PathVariable("ticketIdentifier") String ticketIdentifier,
                                          @RequestBody TicketCode ticketCode,
                                          @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                          Principal principal) {
        String username = principal.getName();
        String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
        return checkInManager.checkIn(eventName, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), username, auditUser);
    }

    @PostMapping("/check-in/event/{eventName}/bulk")
    public Map<String, TicketAndCheckInResult> bulkCheckIn(@PathVariable("eventName") String eventName,
                                                           @RequestBody List<TicketIdentifierCode> ticketIdentifierCodes,
                                                           @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                                           @RequestParam(value = "forceCheckInPaymentOnSite", required = false, defaultValue = "false") boolean forceCheckInPaymentOnSite,
                                                           Principal principal) {
        String username = principal.getName();
        String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
        return ticketIdentifierCodes.stream()
            .map(t -> {
                TicketAndCheckInResult res = checkInManager.checkIn(eventName, t.getIdentifier(),
                    Optional.ofNullable(t.getCode()),
                    username, auditUser, forceCheckInPaymentOnSite);
                return Pair.of(t.identifier, res);
            })
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}/manual-check-in", method = POST)
    public boolean manualCheckIn(@PathVariable("eventId") int eventId,
                                 @PathVariable("ticketIdentifier") String ticketIdentifier,
                                 Principal principal) {
        log.warn("for event id : {} and ticket : {}, a manual check in has been done", eventId, ticketIdentifier);
        return checkInManager.manualCheckIn(eventId, ticketIdentifier, principal.getName());
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}/revert-check-in", method = POST)
    public boolean revertCheckIn(@PathVariable("eventId") int eventId,
                                 @PathVariable("ticketIdentifier") String ticketIdentifier,
                                 Principal principal) {
        log.warn("for event id : {} and ticket : {}, a revert of the check in has been done", eventId, ticketIdentifier);
        return checkInManager.revertCheckIn(eventId, ticketIdentifier, principal.getName());
    }

    @RequestMapping(value = "/check-in/event/{eventName}/ticket/{ticketIdentifier}/confirm-on-site-payment", method = POST)
    public TicketAndCheckInResult confirmOnSitePayment(@PathVariable("eventName") String eventName,
                                                       @PathVariable("ticketIdentifier") String ticketIdentifier,
                                                       @RequestBody TicketCode ticketCode,
                                                       @RequestParam(value = "offlineUser", required = false) String offlineUser,
                                                       Principal principal) {
        String username = principal.getName();
        String auditUser = StringUtils.defaultIfBlank(offlineUser, username);
        return checkInManager.confirmOnSitePayment(eventName, ticketIdentifier, Optional.ofNullable(ticketCode).map(TicketCode::getCode), username, auditUser);
    }

    @RequestMapping(value = "/check-in/event/{eventName}/statistics", method = GET)
    public CheckInStatistics getStatistics(@PathVariable("eventName") String eventName, Principal principal) {
        return checkInManager.getStatistics(eventName, principal.getName());
    }
    
    @RequestMapping(value = "/check-in/{eventId}/ticket/{ticketIdentifier}/confirm-on-site-payment", method = POST)
    public OnSitePaymentConfirmation confirmOnSitePayment(@PathVariable("eventId") int eventId, @PathVariable("ticketIdentifier") String ticketIdentifier) {
        return checkInManager.confirmOnSitePayment(ticketIdentifier)
            .map(s -> new OnSitePaymentConfirmation(true, "ok"))
            .orElseGet(() -> new OnSitePaymentConfirmation(false, "Ticket with uuid " + ticketIdentifier + " not found"));
    }

    @RequestMapping(value = "/check-in/{eventId}/ticket-identifiers", method = GET)
    public List<Integer> findAllIdentifiersForAdminCheckIn(@PathVariable("eventId") int eventId,
                                               @RequestParam(value = "changedSince", required = false) Long changedSince,
                                               HttpServletResponse response,
                                               Principal principal) {
        response.setHeader(ALFIO_TIMESTAMP_HEADER, Long.toString(new Date().getTime()));
        return checkInManager.getAttendeesIdentifiers(eventId, changedSince == null ? new Date(0) : new Date(changedSince), principal.getName());
    }

    @RequestMapping(value = "/check-in/{eventId}/tickets", method = POST)
    public List<FullTicketInfo> findAllTicketsForAdminCheckIn(@PathVariable("eventId") int eventId,
                                                              @RequestBody List<Integer> ids,
                                                              Principal principal) {
        validateIdList(ids);
        return checkInManager.getAttendeesInformation(eventId, ids, principal.getName());
    }

    @RequestMapping(value = "/check-in/{eventName}/label-layout", method = GET)
    public ResponseEntity<LabelLayout> getLabelLayoutForEvent(@PathVariable("eventName") String eventName, Principal principal) {
        return optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()))
            .filter(checkInManager.isOfflineCheckInAndLabelPrintingEnabled())
            .map(this::parseLabelLayout)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.PRECONDITION_FAILED));
    }

    @RequestMapping(value = "/check-in/{eventName}/offline-identifiers", method = GET)
    public List<Integer> getOfflineIdentifiers(@PathVariable("eventName") String eventName,
                                              @RequestParam(value = "changedSince", required = false) Long changedSince,
                                              HttpServletResponse resp,
                                              Principal principal) {
        Date since = changedSince == null ? new Date(0) : DateUtils.addSeconds(new Date(changedSince), -1);
        Optional<List<Integer>> ids = optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()))
            .filter(checkInManager.isOfflineCheckInEnabled())
            .map(event -> checkInManager.getAttendeesIdentifiers(event, since, principal.getName()));

        resp.setHeader(ALFIO_TIMESTAMP_HEADER, ids.isPresent() ? Long.toString(new Date().getTime()) : "0");
        return ids.orElse(Collections.emptyList());
    }

    @RequestMapping(value = "/check-in/{eventName}/offline", method = POST)
    public Map<String, String> getOfflineEncryptedInfo(@PathVariable("eventName") String eventName,
                                                       @RequestParam(value = "additionalField", required = false) List<String> additionalFields,
                                                       @RequestBody List<Integer> ids,
                                                       Principal principal) {

        validateIdList(ids);
        return optionally(() -> eventManager.getSingleEvent(eventName, principal.getName()))
            .map(event -> {
                Set<String> addFields = loadLabelLayout(event)
                    .map(layout -> collectAllFieldNames(additionalFields, layout))
                    .orElseGet(() -> {
                        if(additionalFields != null && !additionalFields.isEmpty()) {
                            return new HashSet<>(additionalFields);
                        }
                        return Collections.singleton("company");
                    });
                return checkInManager.getEncryptedAttendeesInformation(event, addFields, ids);
            }).orElse(Collections.emptyMap());
    }

    private static Set<String> collectAllFieldNames(List<String> additionalFields, LabelLayout layout) {
        Set<String> union = new HashSet<>();
        if(CollectionUtils.isNotEmpty(layout.content.additionalRows)) {
            union.addAll(layout.content.additionalRows);
        }
        if(CollectionUtils.isNotEmpty(layout.content.thirdRow)) {
            union.addAll(layout.content.thirdRow);
        }
        if(CollectionUtils.isNotEmpty(layout.qrCode.additionalInfo)) {
            union.addAll(layout.qrCode.additionalInfo);
        }
        if(CollectionUtils.isNotEmpty(additionalFields)) {
            union.addAll(additionalFields);
        }
        return union;
    }

    private static void validateIdList(@RequestBody List<Integer> ids) {
        Validate.isTrue(ids!= null && !ids.isEmpty());
        Validate.isTrue(ids.size() <= 200, "Cannot ask more than 200 ids");
    }

    private ResponseEntity<LabelLayout> parseLabelLayout(Event event) {
        return loadLabelLayout(event)
            .map(ResponseEntity::ok)
            .orElseGet(() -> new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    private Optional<LabelLayout> loadLabelLayout(Event event) {
        return configurationManager.getStringConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ConfigurationKeys.LABEL_LAYOUT))
            .flatMap(str -> optionally(() -> Json.fromJson(str, LabelLayout.class)));
    }

    @Data
    private static class OnSitePaymentConfirmation {
        private final boolean status;
        private final String message;
    }

    @Getter
    private static class LabelLayout {

        private final QRCode qrCode;
        private final Content content;
        private final General general;

        @JsonCreator
        private LabelLayout(@JsonProperty("qrCode") QRCode qrCode,
                            @JsonProperty("content") Content content,
                            @JsonProperty("general") General general) {
            this.qrCode = qrCode;
            this.content = content;
            this.general = general;
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
                this.thirdRow = thirdRow;
                this.additionalRows = additionalRows;
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
