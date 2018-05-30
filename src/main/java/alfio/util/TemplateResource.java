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

import alfio.model.*;
import alfio.model.modification.SendCodeModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.util.ImageUtil.createQRCode;


public enum TemplateResource {
    GOOGLE_ANALYTICS("/alfio/templates/google-analytics.ms", false, "text/plain", TemplateManager.TemplateOutput.TEXT),

    CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, event);
        }
    },
    SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareModelForSendReservedCode(organization, event, new SendCodeModification("CODE", "Firstname Lastname", "email@email.tld", "en"), "http://your-domain.tld/event-page");
        }
    },
    CONFIRMATION_EMAIL("/alfio/templates/confirmation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, event);
        }
    },
    OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, event);
        }
    },
    OFFLINE_RESERVATION_EXPIRING_EMAIL_FOR_ORGANIZER("/alfio/templates/offline-reservation-expiring-email-for-organizer-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleModelForOfflineReservationExpiringEmailForOrganizer(event);
        }
    },
    REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, event);
        }
    },
    REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareModelForReminderTicketAdditionalInfo(organization, event, sampleTicket(), "http://your-domain.tld/ticket-url");
        }
    },
    REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, event);
        }
    },


    TICKET_EMAIL("/alfio/templates/ticket-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            TicketCategory ticketCategory = new TicketCategory(0, ZonedDateTime.now(), ZonedDateTime.now(), 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null);
            return buildModelForTicketEmail(organization, event, sampleTicketReservation(), "http://your-domain.tld/ticket-url", sampleTicket(), ticketCategory);
        }
    },
    TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return buildModelForTicketHasChangedOwner(organization, event, sampleTicket(), sampleTicket("NewFirstname", "NewLastname", "newemail@email.tld"), "http://your-domain.tld/ticket-url");
        }
    },

    TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return buildModelForTicketHasBeenCancelled(organization, event, sampleTicket());
        }
    },

    TICKET_HAS_BEEN_CANCELLED_ADMIN("/alfio/templates/ticket-has-been-cancelled-admin-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return buildModelForTicketHasBeenCancelledAdmin(organization, event, sampleTicket(), "Category", Collections.emptyList(), asi -> Optional.empty());
        }
    },


    TICKET_PDF("/alfio/templates/ticket.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            TicketCategory ticketCategory = new TicketCategory(0, ZonedDateTime.now(), ZonedDateTime.now(), 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null);
            return buildModelForTicketPDF(organization, event, sampleTicketReservation(), ticketCategory, sampleTicket(), imageData, "ABCD");
        }
    },
    RECEIPT_PDF("/alfio/templates/receipt.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            Map<String, Object> model = prepareSampleDataForConfirmationEmail(organization, event);
            imageData.ifPresent(iData -> {
                model.put("eventImage", iData.getEventImage());
                model.put("imageWidth", iData.getImageWidth());
                model.put("imageHeight", iData.getImageHeight());
            });
            return model;
        }
    },

    INVOICE_PDF("/alfio/templates/invoice.ms", true, "application/pdf", TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            Map<String, Object> model = prepareSampleDataForConfirmationEmail(organization, event);
            imageData.ifPresent(iData -> {
                model.put("eventImage", iData.getEventImage());
                model.put("imageWidth", iData.getImageWidth());
                model.put("imageHeight", iData.getImageHeight());
            });
            return model;
        }
    },

    WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            return buildModelForWaitingQueueJoined(organization, event, new CustomerName("Firstname Lastname", "Firstname", "Lastname", event));
        }
    },
    WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms", true, "text/plain", TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
            WaitingQueueSubscription subscription = new WaitingQueueSubscription(0, ZonedDateTime.now(), event.getId(), "ACQUIRED", "Firstname Lastname", "Firstname", "Lastname",
                "email@email.tld", "597e7e7b-c514-4dcb-be8c-46cf7fe2c36e", "en", null, WaitingQueueSubscription.Type.PRE_SALES);
            return buildModelForWaitingQueueReservationEmail(organization, event, subscription, "http://your-domain.tld/reservation-url", ZonedDateTime.now());
        }
    };

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

    public String getRenderedContentType() {
        return renderedContentType;
    }

    public TemplateManager.TemplateOutput getTemplateOutput() {
        return templateOutput;
    }

    public Map<String, Object> prepareSampleModel(Organization organization, Event event, Optional<ImageData> imageData) {
        return Collections.emptyMap();
    }

    private static Ticket sampleTicket() {
        return sampleTicket("Firstname", "Lastname", "email@email.tld");
    }

    private static TicketCategory sampleCategory() {
        return new TicketCategory(0, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), 100, "test category", false, TicketCategory.Status.ACTIVE,
            0, true, 100, null, null, null, null, null);
    }

    private static Ticket sampleTicket(String firstName, String lastName, String email) {
        return new Ticket(0, "597e7e7b-c514-4dcb-be8c-46cf7fe2c36e", ZonedDateTime.now(), 0, "ACQUIRED", 0,
            "597e7e7b-c514-4dcb-be8c-46cf7fe2c36e", firstName + " " + lastName, firstName, lastName, email, false, "en",
            1000, 1000, 80, 0, null);
    }

    private static TicketReservation sampleTicketReservation() {
        return new TicketReservation("597e7e7b-c514-4dcb-be8c-46cf7fe2c36e", new Date(), TicketReservation.TicketReservationStatus.COMPLETE,
            "Firstname Lastname", "FirstName", "Lastname", "email@email.tld", "billing address", ZonedDateTime.now(), ZonedDateTime.now(),
            PaymentProxy.STRIPE, true, null, false, "en", false, null, null, null, "123456",
            "CH", false, new BigDecimal("8.00"), true,
            ZonedDateTime.now().minusMinutes(1), "PO-1234");
    }

    private static Map<String, Object> prepareSampleDataForConfirmationEmail(Organization organization, Event event) {
        TicketReservation reservation = sampleTicketReservation();
        Optional<String> vat = Optional.of("VAT-NR");
        List<TicketWithCategory> tickets = Collections.singletonList(new TicketWithCategory(sampleTicket(), sampleCategory()));
        OrderSummary orderSummary = new OrderSummary(new TotalPrice(1000, 80, 0, 0),
            Collections.singletonList(new SummaryRow("Ticket", "10.00", "9.20", 1, "9.20", "9.20", 1000, SummaryRow.SummaryType.TICKET)), false, "10.00", "0.80", false, false, "8", PriceContainer.VatStatus.INCLUDED, "1.00");
        String reservationUrl = "http://your-domain.tld/reservation-url/";
        String reservationShortId = "597e7e7b";
        return prepareModelForConfirmationEmail(organization, event, reservation, vat, tickets, orderSummary, reservationUrl, reservationShortId, Optional.of("My Invoice\nAddress"), Optional.empty(), Optional.empty());
    }

    //used by multiple enum:
    // - CONFIRMATION_EMAIL_FOR_ORGANIZER
    // - OFFLINE_RESERVATION_EXPIRED_EMAIL
    // - REMINDER_TICKETS_ASSIGNMENT_EMAIL
    // - CONFIRMATION_EMAIL
    // - REMINDER_EMAIL
    // - RECEIPT_PDF + ImageData
    // - INVOICE_PDF + ImageData
    public static Map<String, Object> prepareModelForConfirmationEmail(Organization organization,
                                                                       Event event,
                                                                       TicketReservation reservation,
                                                                       Optional<String> vat,
                                                                       List<TicketWithCategory> tickets,
                                                                       OrderSummary orderSummary,
                                                                       String reservationUrl,
                                                                       String reservationShortID,
                                                                       Optional<String> invoiceAddress,
                                                                       Optional<String> bankAccountNr,
                                                                       Optional<String> bankAccountOwner) {
        Map<String, Object> model = new HashMap<>();
        model.put("organization", organization);
        model.put("event", event);
        model.put("ticketReservation", reservation);
        model.put("hasVat", vat.isPresent());
        model.put("vatNr", vat.orElse(""));
        model.put("tickets", tickets);
        model.put("orderSummary", orderSummary);
        model.put("reservationUrl", reservationUrl);
        model.put("locale", reservation.getUserLanguage());

        model.put("hasRefund", StringUtils.isNotEmpty(orderSummary.getRefundedAmount()));

        ZonedDateTime creationTimestamp = ObjectUtils.firstNonNull(reservation.getCreationTimestamp(), reservation.getConfirmationTimestamp(), ZonedDateTime.now());
        model.put("confirmationDate", creationTimestamp.withZoneSameInstant(event.getZoneId()));

        if (reservation.getValidity() != null) {
            model.put("expirationDate", ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), event.getZoneId()));
        }

        model.put("reservationShortID", reservationShortID);

        model.put("hasInvoiceAddress", invoiceAddress.isPresent());
        invoiceAddress.ifPresent(addr -> {
            model.put("invoiceAddress", StringUtils.replace(addr, "\n", ", "));
            model.put("invoiceAddressAsList", Arrays.asList(StringUtils.split(addr, '\n')));
        });

        model.put("hasBankAccountNr", bankAccountNr.isPresent());
        bankAccountNr.ifPresent(nr -> {
            model.put("bankAccountNr", nr);
        });

        model.put("isOfflinePayment", reservation.getStatus() == TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);
        model.put("hasCustomerReference", StringUtils.isNotBlank(reservation.getCustomerReference()));
        model.put("paymentReason", event.getShortName() + " " + reservationShortID);
        model.put("hasBankAccountOnwer", bankAccountOwner.isPresent());
        bankAccountOwner.ifPresent(owner -> {
            model.put("bankAccountOnwer", StringUtils.replace(owner, "\n", ", "));
            model.put("bankAccountOnwerAsList", Arrays.asList(StringUtils.split(owner, '\n')));
        });

        return model;
    }

    // used by OFFLINE_RESERVATION_EXPIRING_EMAIL_FOR_ORGANIZER
    public static Map<String, Object> prepareModelForOfflineReservationExpiringEmailForOrganizer(Event event, List<TicketReservationInfo> reservations, String baseUrl) {
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("ticketReservations", reservations.stream().map(r -> new TicketReservationWithZonedExpiringDate(r, event)).collect(Collectors.toList()));
        model.put("baseUrl", baseUrl);
        model.put("eventShortName", event.getShortName());
        return model;
    }

    public static Map<String, Object> prepareSampleModelForOfflineReservationExpiringEmailForOrganizer(Event event) {
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("ticketReservations", Collections.singletonList(new TicketReservationInfo("id", null, "Firstname", "Lastname", "email@email.email", 42, new Date())));
        model.put("baseUrl", "http://base-url/");
        model.put("eventShortName", event.getShortName());
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
                                                               Ticket ticket,
                                                               TicketCategory ticketCategory) {
        Map<String, Object> model = new HashMap<>();
        model.put("organization", organization);
        model.put("event", event);
        model.put("ticketReservation", ticketReservation);
        model.put("ticketUrl", ticketURL);
        model.put("ticket", ticket);
        model.put("googleCalendarUrl", EventUtil.getGoogleCalendarURL(event, ticketCategory, null));
        fillTicketValidity(event, ticketCategory, model);
        return model;
    }

    public static void fillTicketValidity(Event event, TicketCategory ticketCategory, Map<String, Object> model) {
        model.put("validityStart", Optional.ofNullable(ticketCategory.getTicketValidityStart(event.getZoneId())).orElse(event.getBegin()));
        model.put("validityEnd", Optional.ofNullable(ticketCategory.getTicketValidityEnd(event.getZoneId())).orElse(event.getEnd()));
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

    // used by TICKET_HAS_BEEN_CANCELLED_ADMIN
    public static Map<String, Object> buildModelForTicketHasBeenCancelledAdmin(Organization organization, Event event, Ticket ticket,
                                                                               String ticketCategoryDescription,
                                                                               List<AdditionalServiceItem> additionalServiceItems,
                                                                               Function<AdditionalServiceItem, Optional<AdditionalServiceText>> titleFinder) {
        Map<String, Object> model = buildModelForTicketHasBeenCancelled(organization, event, ticket);
        model.put("ticketCategoryDescription", ticketCategoryDescription);
        model.put("hasAdditionalServices", !additionalServiceItems.isEmpty());
        model.put("additionalServices", additionalServiceItems.stream()
            .map(asi -> {
                Map<String, Object> data = new HashMap<>();
                data.put("name", titleFinder.apply(asi).map(AdditionalServiceText::getValue).orElse("N/A"));
                data.put("amount", MonetaryUtil.centsToUnit(asi.getFinalPriceCts()).toString() + event.getCurrency());
                data.put("id", asi.getId());
                return data;
            }).collect(Collectors.toList()));
        return model;
    }

    // used by TICKET_PDF
    public static Map<String, Object> buildModelForTicketPDF(Organization organization, Event event, TicketReservation ticketReservation, TicketCategory ticketCategory, Ticket ticket, Optional<ImageData> imageData, String reservationId) {
        String qrCodeText = ticket.ticketCode(event.getPrivateKey());
        //
        Map<String, Object> model = new HashMap<>();
        model.put("ticket", ticket);
        model.put("reservation", ticketReservation);
        model.put("ticketCategory", ticketCategory);
        model.put("event", event);
        model.put("organization", organization);
        model.put("reservationId", reservationId);
        fillTicketValidity(event, ticketCategory, model);

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


    public static ImageData fillWithImageData(FileBlobMetadata m, byte[] image) {

        Map<String, String> attributes = m.getAttributes();
        if (attributes.containsKey(FileBlobMetadata.ATTR_IMG_WIDTH) && attributes.containsKey(FileBlobMetadata.ATTR_IMG_HEIGHT)) {
            final int width = Integer.parseInt(attributes.get(FileBlobMetadata.ATTR_IMG_WIDTH));
            final int height = Integer.parseInt(attributes.get(FileBlobMetadata.ATTR_IMG_HEIGHT));
            //in the PDF the image can be maximum 300x150
            int resizedWidth = width;
            int resizedHeight = height;
            if (resizedHeight > 150) {
                resizedHeight = 150;
                resizedWidth = width * resizedHeight / height;
            }
            if (resizedWidth > 300) {
                resizedWidth = 300;
                resizedHeight = height * resizedWidth / width;
            }
            return new ImageData("data:" + m.getContentType() + ";base64," + Base64.getEncoder().encodeToString(image), resizedWidth, resizedHeight);
        }
        return new ImageData(null, null, null);
    }

    @Getter
    @AllArgsConstructor
    public static class ImageData {
        private final String eventImage;
        private final Integer imageWidth;
        private final Integer imageHeight;
    }

    @Getter
    @AllArgsConstructor
    public static class TicketReservationWithZonedExpiringDate {
        @Delegate
        private final TicketReservationInfo reservation;
        private final Event event;

        public ZonedDateTime getZonedExpiration() {
            return reservation.getValidity().toInstant().atZone(event.getZoneId());
        }
    }
}
