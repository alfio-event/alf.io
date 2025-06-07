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

import alfio.controller.api.support.BookingInfoTicketLoader;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.DatesWithTimeZoneOffset;
import alfio.controller.api.v2.model.OnlineCheckInInfo;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.controller.api.v2.model.TicketInfo;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.Formatters;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.AdditionalServiceHelper;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ImageUtil;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static alfio.controller.Constants.TICKET_PDF_URI;
import static alfio.controller.Constants.TICKET_QR_CODE_URI;
import static alfio.model.PurchaseContextFieldConfiguration.EVENT_RELATED_CONTEXTS;
import static alfio.util.EventUtil.firstMatchingCallLink;
import static alfio.util.ExportUtils.markAsNoIndex;

@RestController
public class TicketApiV2Controller {

    private final TicketHelper ticketHelper;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final MessageSourceManager messageSourceManager;
    private final ExtensionManager extensionManager;
    private final FileUploadManager fileUploadManager;
    private final OrganizationRepository organizationRepository;
    private final TemplateManager templateManager;
    private final NotificationManager notificationManager;
    private final BookingInfoTicketLoader bookingInfoTicketLoader;
    private final TicketRepository ticketRepository;
    private final SubscriptionManager subscriptionManager;
    private final ConfigurationManager configurationManager;
    private final AdditionalServiceHelper additionalServiceHelper;

    public TicketApiV2Controller(TicketHelper ticketHelper, TicketReservationManager ticketReservationManager, TicketCategoryRepository ticketCategoryRepository, MessageSourceManager messageSourceManager, ExtensionManager extensionManager, FileUploadManager fileUploadManager, OrganizationRepository organizationRepository, TemplateManager templateManager, NotificationManager notificationManager, BookingInfoTicketLoader bookingInfoTicketLoader, TicketRepository ticketRepository, SubscriptionManager subscriptionManager, ConfigurationManager configurationManager, AdditionalServiceHelper additionalServiceHelper) {
        this.ticketHelper = ticketHelper;
        this.ticketReservationManager = ticketReservationManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.messageSourceManager = messageSourceManager;
        this.extensionManager = extensionManager;
        this.fileUploadManager = fileUploadManager;
        this.organizationRepository = organizationRepository;
        this.templateManager = templateManager;
        this.notificationManager = notificationManager;
        this.bookingInfoTicketLoader = bookingInfoTicketLoader;
        this.ticketRepository = ticketRepository;
        this.subscriptionManager = subscriptionManager;
        this.configurationManager = configurationManager;
        this.additionalServiceHelper = additionalServiceHelper;
    }


    @GetMapping(value = {
        TICKET_QR_CODE_URI,
        "/event/{eventName}/ticket/{ticketIdentifier}/code.png"
    })
    public void showQrCode(@PathVariable String eventName,
                           @PathVariable UUID ticketIdentifier,
                           HttpServletResponse response) throws IOException {
        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        if (oData.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        var event = oData.get().getLeft();
        var ticket = oData.get().getRight();

        String qrCodeText = ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive());

        response.setContentType("image/png");
        markAsNoIndex(response);

        try (var os = response.getOutputStream()) {
            os.write(ImageUtil.createQRCode(qrCodeText));
            response.flushBuffer();
        }
    }

    @GetMapping(TICKET_PDF_URI)
    public void generateTicketPdf(@PathVariable String eventName,
                                  @PathVariable UUID ticketIdentifier,
                                  HttpServletResponse response) {

        ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier).ifPresentOrElse(data -> {

            Ticket ticket = data.getRight();
            Event event = data.getLeft();
            TicketReservation ticketReservation = data.getMiddle();

            response.setContentType(MediaType.APPLICATION_PDF_VALUE);
            response.addHeader("Content-Disposition", "attachment; filename=ticket-" + ticketIdentifier + ".pdf");
            markAsNoIndex(response);
            try (OutputStream os = response.getOutputStream()) {
                TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                String reservationID = configurationManager.getShortReservationID(event, ticketReservation);
                var ticketWithMetadata = TicketWithMetadataAttributes.build(ticket, ticketRepository.getTicketMetadata(ticket.getId()));
                var locale = LocaleUtil.getTicketLanguage(ticket, LocaleUtil.forLanguageTag(ticketReservation.getUserLanguage(), event));
                TemplateProcessor.renderPDFTicket(
                    locale, event, ticketReservation,
                    ticketWithMetadata, ticketCategory, organization,
                    templateManager, fileUploadManager,
                    reservationID, os, ticketHelper.buildRetrieveFieldValuesFunction(true), extensionManager,
                    TemplateProcessor.getSubscriptionDetailsModelForTicket(ticket, subscriptionManager::findDescriptorBySubscriptionId, locale),
                    additionalServiceHelper.findForTicket(ticket, event)
                );
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        }, () -> {
            try {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } catch (IOException ioe) {
                throw new IllegalStateException(ioe);
            }
        });
    }

    @PostMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}/send-ticket-by-email")
    public ResponseEntity<Boolean> sendTicketByEmail(@PathVariable String eventName,
                                                     @PathVariable UUID ticketIdentifier) {

        return ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier).map(data -> {
            Event event = data.getLeft();
            TicketReservation reservation = data.getMiddle();
            Ticket ticket = data.getRight();

            Locale locale = LocaleUtil.getTicketLanguage(ticket, LocaleUtil.forLanguageTag(reservation.getUserLanguage(), event));
            notificationManager.sendTicketByEmail(
                ticket,
                event,
                locale,
                ticketHelper.getConfirmationTextBuilder(locale, event, reservation, ticket),
                reservation,
                ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId()),
                () -> ticketReservationManager.retrieveAttendeeAdditionalInfoForTicket(ticket)
            );
            return ResponseEntity.ok(true);

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<Boolean> releaseTicket(@PathVariable String eventName,
                                                 @PathVariable UUID ticketIdentifier) {
        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        try {
            oData.ifPresent(triple -> ticketReservationManager.releaseTicket(triple.getLeft(), triple.getMiddle(), triple.getRight()));
            return ResponseEntity.ok(true);
        } catch (IllegalStateException ise) {
            return ResponseEntity.badRequest().body(false);
        }
    }

    @GetMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}/full")
    public ResponseEntity<ReservationInfo.TicketsByTicketCategory> getTicket(@PathVariable String eventName,
                                                                             @PathVariable UUID ticketIdentifier) {

        var optionalTicket = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier)
            .map(complete -> {
                var ticket = complete.getRight();
                var event = complete.getLeft();

                var category = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
                return new ReservationInfo.TicketsByTicketCategory(category.getName(), category.getTicketAccessType(), List.of(bookingInfoTicketLoader.toBookingInfoTicket(ticket, event, EVENT_RELATED_CONTEXTS)));
            });
        return ResponseEntity.of(optionalTicket);
    }

    @GetMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<TicketInfo> getTicketInfo(@PathVariable String eventName,
                                                    @PathVariable UUID ticketIdentifier) {

        //TODO: cleanup, we load useless data here!

        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        if(oData.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var data = oData.get();


        TicketReservation ticketReservation = data.getMiddle();
        Ticket ticket = data.getRight();
        Event event = data.getLeft();

        TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());

        boolean deskPaymentRequired = Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired();


        //
        var validityStart = Optional.ofNullable(ticketCategory.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin());
        var validityEnd = Optional.ofNullable(ticketCategory.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd());
        var sameDay = validityStart.truncatedTo(ChronoUnit.DAYS).equals(validityEnd.truncatedTo(ChronoUnit.DAYS));


        var messageSource = messageSourceManager.getMessageSourceFor(event);
        var formattedDates = Formatters.getFormattedDates(event, messageSource, event.getContentLanguages());
        //


        return ResponseEntity.ok(new TicketInfo(
            ticket.getFullName(),
            ticket.getEmail(),
            ticket.getPublicUuid().toString(),
            ticketCategory.getName(),
            ticketReservation.getFullName(),
            configurationManager.getShortReservationID(event, ticketReservation),
            deskPaymentRequired,
            event.getTimeZone(),
            DatesWithTimeZoneOffset.fromEvent(event),
            sameDay,
            formattedDates.beginDate,
            formattedDates.beginTime,
            formattedDates.endDate,
            formattedDates.endTime,
            additionalServiceHelper.findForTicket(ticket, event)
        ));
    }

    @PutMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<ValidatedResponse<Boolean>> updateTicketInfo(@PathVariable String eventName,
                                                                       @PathVariable UUID ticketIdentifier,
                                                                       @RequestBody UpdateTicketOwnerForm updateTicketOwner,
                                                                       BindingResult bindingResult,
                                                                       Authentication authentication) {

        var a = ticketReservationManager.fetchComplete(eventName, ticketIdentifier);
        if (a.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<UserDetails> userDetails = Optional.ofNullable(authentication)
            .map(Authentication::getPrincipal)
            .filter(UserDetails.class::isInstance)
            .map(UserDetails.class::cast);

        Locale locale = LocaleUtil.forLanguageTag(a.get().getMiddle().getUserLanguage(), a.get().getLeft());

        var assignmentResult = ticketHelper.assignTicket(eventName,
            ticketIdentifier,
            updateTicketOwner,
            Optional.of(bindingResult),
            locale,
            userDetails, false);

        return assignmentResult.map(r ->
            ResponseEntity.status(r.getLeft().isSuccess() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ValidatedResponse<>(r.getLeft(), r.getLeft().isSuccess()))
        ).orElseThrow(IllegalStateException::new);
    }

    @GetMapping("/api/v2/public/event/{eventName}/ticket/{ticketIdentifier}/code/{checkInCode}/check-in-info")
    public ResponseEntity<OnlineCheckInInfo> getCheckInInfo(@PathVariable String eventName,
                                                            @PathVariable UUID ticketIdentifier,
                                                            @PathVariable String checkInCode,
                                                            @RequestParam(value = "tz", required = false) String userTz) {
        return ResponseEntity.of(ticketReservationManager.fetchCompleteAndAssignedForOnlineCheckIn(eventName, ticketIdentifier)
            .flatMap(info -> {
                var ticket = info.getTicket();
                var event = info.getEventWithCheckInInfo();
                var messageSource = messageSourceManager.getMessageSourceFor(event.getOrganizationId(), event.getId());
                String ticketCode = ticket.ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive());
                if(MessageDigest.isEqual(DigestUtils.sha256Hex(ticketCode).getBytes(StandardCharsets.UTF_8), checkInCode.getBytes(StandardCharsets.UTF_8))) {
                    var categoryConfiguration = info.getCategoryMetadata().getOnlineConfiguration();
                    var eventConfiguration = event.getMetadata().getOnlineConfiguration();
                    var zoneId = StringUtils.isNotBlank(userTz) ? ZoneId.of(userTz) : event.getZoneId();
                    return firstMatchingCallLink(event.getZoneId(), categoryConfiguration, eventConfiguration)
                        .map(joinLink -> OnlineCheckInInfo.fromJoinLink(joinLink, event, zoneId, messageSource))
                        .or(() -> Optional.of(OnlineCheckInInfo.fromEvent(event, zoneId, messageSource)));
                } else {
                    return Optional.empty();
                }
            }));
    }
}
