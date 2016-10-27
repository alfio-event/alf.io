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

package alfio.util;

import alfio.controller.support.TemplateProcessor;
import alfio.manager.support.OrderSummary;
import alfio.model.*;
import alfio.model.modification.SendCodeModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;

import java.time.ZonedDateTime;
import java.util.*;

import static alfio.util.ImageUtil.createQRCode;

public enum TemplateResource {
    GOOGLE_ANALYTICS("/alfio/templates/google-analytics.ms", false, "text/plain", TemplateManager.TemplateOutput.TEXT),
    CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    CONFIRMATION_EMAIL("/alfio/templates/confirmation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),


    TICKET_EMAIL("/alfio/templates/ticket-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),

    TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    TICKET_PDF("/alfio/templates/ticket.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML),
    RECEIPT_PDF("/alfio/templates/receipt.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML),

    WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT),
    WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT);

    private final String classPathUrl;
    private final boolean overridable;
    private final String renderedContentType;
    private final TemplateManager.TemplateOutput templateOutput;

    TemplateResource(String classPathUrl, boolean overridable, String renderedContentType, TemplateManager.TemplateOutput templateOutput) {
        this.classPathUrl = classPathUrl;
        this.overridable = overridable;
        this.renderedContentType = renderedContentType;
        this.templateOutput = templateOutput;
    }



    public String getSavedName(Locale locale) {
        return name() + "_" + locale.getLanguage() + ".ms";
    }

    public boolean overridable() {
        return overridable;
    }

    public String classPath() {
        return classPathUrl;
    }

    public TemplateManager.TemplateOutput getTemplateOutput() {
        return templateOutput;
    }

    //used by multiple enum:
    // - CONFIRMATION_EMAIL_FOR_ORGANIZER
    // - OFFLINE_RESERVATION_EXPIRED_EMAIL
    // - REMINDER_TICKETS_ASSIGNMENT_EMAIL
    // - CONFIRMATION_EMAIL
    // - REMINDER_EMAIL
    // - RECEIPT_PDF + ImageData
    public static Map<String, Object> prepareModelForConfirmationEmail(Organization organization,
                                                                       Event event,
                                                                       TicketReservation reservation,
                                                                       Optional<String> vat,
                                                                       List<Ticket> tickets,
                                                                       OrderSummary orderSummary,
                                                                       String reservationUrl,
                                                                       String reservationShortID) {
        Map<String, Object> model = new HashMap<>();
        model.put("organization", organization);
        model.put("event", event);
        model.put("ticketReservation", reservation);
        model.put("hasVat", vat.isPresent());
        model.put("vatNr", vat.orElse(""));
        model.put("tickets", tickets);
        model.put("orderSummary", orderSummary);
        model.put("reservationUrl", reservationUrl);

        ZonedDateTime confirmationTimestamp = Optional.ofNullable(reservation.getConfirmationTimestamp()).orElseGet(ZonedDateTime::now);
        model.put("confirmationDate", confirmationTimestamp.withZoneSameInstant(event.getZoneId()));

        if(reservation.getValidity() != null) {
            model.put("expirationDate", ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), event.getZoneId()));
        }

        model.put("reservationShortID", reservationShortID);

        return model;
    }

    // used by SEND_RESERVED_CODE
    public static Map<String, Object> prepareModelForSendReservedCode(Organization organization,
                                                                      Event event,
                                                                      SendCodeModification m,
                                                                      String eventPageUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("code", m.getCode());
        model.put("event", event);
        model.put("organization", organization);
        model.put("eventPage", eventPageUrl);
        model.put("assignee", m.getAssignee());
        return model;
    }


    // used by REMINDER_TICKET_ADDITIONAL_INFO
    public static Map<String, Object> prepareModelForReminderTicketAdditionalInfo(Organization organization,
                                                                                  Event event,
                                                                                  Ticket ticket,
                                                                                  String ticketUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("event", event);
        model.put("fullName", ticket.getFullName());
        model.put("organization", organization);
        model.put("ticketURL", ticketUrl);
        return model;
    }

    // used by TICKET_EMAIL
    public static Map<String, Object> buildModelForTicketEmail(Organization organization,
                                                               Event event,
                                                               TicketReservation ticketReservation,
                                                               String ticketURL,
                                                               Ticket ticket) {
        Map<String, Object> model = new HashMap<>();
        model.put("organization", organization);
        model.put("event", event);
        model.put("ticketReservation", ticketReservation);
        model.put("ticketUrl", ticketURL);
        model.put("ticket", ticket);
        return model;
    }

    // used by TICKET_HAS_CHANGED_OWNER
    public static Map<String, Object> buildModelForTicketHasChangedOwner(Organization organization, Event e, Ticket oldTicket, Ticket newTicket, String ticketUrl) {
        Map<String, Object> emailModel = new HashMap<>();
        emailModel.put("ticket", oldTicket);
        emailModel.put("organization", organization);
        emailModel.put("eventName", e.getDisplayName());
        emailModel.put("previousEmail", oldTicket.getEmail());
        emailModel.put("newEmail", newTicket.getEmail());
        emailModel.put("ticketUrl", ticketUrl);
        return emailModel;
    }

    // used by TICKET_HAS_BEEN_CANCELLED
    public static Map<String, Object> buildModelForTicketHasBeenCancelled(Organization organization, Event event, Ticket ticket) {
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("ticket", ticket);
        model.put("organization", organization);
        return model;
    }

    // used by TICKET_PDF
    public static Map<String, Object> buildModelForTicketPDF(Organization organization, Event event, TicketReservation ticketReservation, TicketCategory ticketCategory, Ticket ticket, Optional<TemplateProcessor.ImageData> imageData) {
        String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
        //
        Map<String, Object> model = new HashMap<>();
        model.put("ticket", ticket);
        model.put("reservation", ticketReservation);
        model.put("ticketCategory", ticketCategory);
        model.put("event", event);
        model.put("organization", organization);

        model.put("qrCodeDataUri", "data:image/png;base64," + Base64.getEncoder().encodeToString(createQRCode(qrCodeText)));

        imageData.ifPresent(iData -> {
            model.put("eventImage", iData.getEventImage());
            model.put("imageWidth", iData.getImageWidth());
            model.put("imageHeight", iData.getEventImage());
        });

        model.put("deskPaymentRequired", Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired());
        return model;
    }

    // used by WAITING_QUEUE_JOINED
    public static Map<String, Object> buildModelForWaitingQueueJoined(Organization organization, Event event, CustomerName name) {
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("fullName", name.getFullName());
        model.put("organization", organization);
        return model;
    }

    // used by WAITING_QUEUE_RESERVATION_EMAIL
    public static Map<String, Object> buildModelForWaitingQueueReservationEmail(Organization organization, Event event, WaitingQueueSubscription subscription, String reservationUrl, ZonedDateTime expiration) {
        Map<String, Object> model = new HashMap<>();
        model.put("event", event);
        model.put("subscription", subscription);
        model.put("reservationUrl", reservationUrl);
        model.put("reservationTimeout", expiration);
        model.put("organization", organization);
        return model;
    }

}
