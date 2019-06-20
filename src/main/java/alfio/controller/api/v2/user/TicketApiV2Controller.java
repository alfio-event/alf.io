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

import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.model.TicketInfo;
import alfio.controller.api.v2.model.ValidatedResponse;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.Formatters;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.ExtensionManager;
import alfio.manager.FileUploadManager;
import alfio.manager.NotificationManager;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import alfio.util.ImageUtil;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/public/")
public class TicketApiV2Controller {

    private final TicketHelper ticketHelper;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final CustomResourceBundleMessageSource messageSource;
    private final ExtensionManager extensionManager;
    private final FileUploadManager fileUploadManager;
    private final OrganizationRepository organizationRepository;
    private final TemplateManager templateManager;
    private final NotificationManager notificationManager;


    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}/code.png")
    public void showQrCode(@PathVariable("eventName") String eventName,
                           @PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletResponse response) throws IOException {
        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        if(oData.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        var event = oData.get().getLeft();
        var ticket = oData.get().getRight();

        String qrCodeText =  ticket.ticketCode(event.getPrivateKey());

        response.setContentType("image/png");

        try(var os = response.getOutputStream()) {
            os.write(ImageUtil.createQRCode(qrCodeText));
            response.flushBuffer();
        }
    }

    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}/download-ticket")
    public void generateTicketPdf(@PathVariable("eventName") String eventName,
                                  @PathVariable("ticketIdentifier") String ticketIdentifier,
                                  HttpServletResponse response) {

        ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier).ifPresentOrElse(data -> {

            Ticket ticket = data.getRight();
            Event event = data.getLeft();
            TicketReservation ticketReservation = data.getMiddle();

            response.setContentType("application/pdf");
            response.addHeader("Content-Disposition", "attachment; filename=ticket-" + ticketIdentifier + ".pdf");
            try (OutputStream os = response.getOutputStream()) {
                TicketCategory ticketCategory = ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId());
                Organization organization = organizationRepository.getById(event.getOrganizationId());
                String reservationID = ticketReservationManager.getShortReservationID(event, ticketReservation);
                TemplateProcessor.renderPDFTicket(LocaleUtil.getTicketLanguage(ticket, LocaleUtil.forLanguageTag(ticketReservation.getUserLanguage(), event)), event, ticketReservation,
                    ticket, ticketCategory, organization,
                    templateManager, fileUploadManager,
                    reservationID, os, ticketHelper.buildRetrieveFieldValuesFunction(), extensionManager);
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

    @PostMapping("/event/{eventName}/ticket/{ticketIdentifier}/send-ticket-by-email")
    public ResponseEntity<Boolean> sendTicketByEmail(@PathVariable("eventName") String eventName,
                                                     @PathVariable("ticketIdentifier") String ticketIdentifier) {

        return ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier).map(data -> {
            Event event = data.getLeft();
            TicketReservation reservation = data.getMiddle();
            Ticket ticket = data.getRight();

            Locale locale = LocaleUtil.getTicketLanguage(ticket, LocaleUtil.forLanguageTag(reservation.getUserLanguage(), event));
            Organization organization = organizationRepository.getById(event.getOrganizationId());
            TicketCategory category = ticketCategoryRepository.getById(ticket.getCategoryId());
            notificationManager.sendTicketByEmail(ticket,
                event, locale, TemplateProcessor.buildPartialEmail(event, organization, reservation, category, templateManager, ticketReservationManager.ticketUpdateUrl(event, ticket.getUuid()), locale),
                reservation, ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), event.getId()));
            return ResponseEntity.ok(true);

        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<Boolean> releaseTicket(@PathVariable("eventName") String eventName,
                                                 @PathVariable("ticketIdentifier") String ticketIdentifier) {
        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        try {
            oData.ifPresent(triple -> ticketReservationManager.releaseTicket(triple.getLeft(), triple.getMiddle(), triple.getRight()));
            return ResponseEntity.ok(true);
        } catch (IllegalStateException ise) {
            return ResponseEntity.badRequest().body(false);
        }
    }


    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<TicketInfo> getTicketInfo(@PathVariable("eventName") String eventName,
                                                    @PathVariable("ticketIdentifier") String ticketIdentifier) {

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


        var formattedBeginDate = Formatters.getFormattedDate(event, validityStart, "common.event.date-format", messageSource);
        var formattedBeginTime = Formatters.getFormattedDate(event, validityStart, "common.event.time-format", messageSource);
        var formattedEndDate = Formatters.getFormattedDate(event, validityEnd, "common.event.date-format", messageSource);
        var formattedEndTime = Formatters.getFormattedDate(event, validityEnd, "common.event.time-format", messageSource);
        //


        return ResponseEntity.ok(new TicketInfo(
            ticket.getFullName(),
            ticket.getEmail(),
            ticket.getUuid(),
            ticketCategory.getName(),
            ticketReservation.getFullName(),
            ticketReservationManager.getShortReservationID(event, ticketReservation),
            deskPaymentRequired,
            event.getTimeZone(),
            sameDay,
            formattedBeginDate,
            formattedBeginTime,
            formattedEndDate,
            formattedEndTime)
        );
    }

    @PutMapping("/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<ValidatedResponse<Boolean>> updateTicketInfo(@PathVariable("eventName") String eventName,
                                                                       @PathVariable("ticketIdentifier") String ticketIdentifier,
                                                                       @RequestBody UpdateTicketOwnerForm updateTicketOwner,
                                                                       BindingResult bindingResult,
                                                                       Authentication authentication) {

        var a = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        if(a.isEmpty()) {
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

        return assignmentResult.map(r -> ResponseEntity.ok(new ValidatedResponse<>(r.getLeft(), r.getLeft().isSuccess()))).orElseThrow(IllegalStateException::new);
    }

}
