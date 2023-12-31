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

import alfio.controller.api.support.AdditionalServiceWithData;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.api.v1.admin.subscription.SubscriptionConfiguration;
import alfio.model.metadata.SubscriptionMetadata;
import alfio.model.modification.SendCodeModification;
import alfio.model.subscription.Subscription;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionValidityType;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Delegate;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.util.ImageUtil.createQRCode;
import static alfio.util.MustacheCustomTag.SUBSCRIPTION_DESCRIPTOR_ATTRIBUTE;
import static alfio.util.TemplateManager.ADDITIONAL_FIELDS_KEY;
import static alfio.util.TemplateManager.METADATA_ATTRIBUTES_KEY;
import static java.util.Objects.requireNonNullElse;
import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;


public enum TemplateResource {

    @Deprecated
    GOOGLE_ANALYTICS("", "", TemplateManager.TemplateOutput.TEXT),

    CONFIRMATION_EMAIL_FOR_ORGANIZER("/alfio/templates/confirmation-email-for-organizer", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },
    SEND_RESERVED_CODE("/alfio/templates/send-reserved-code-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareModelForSendReservedCode(organization, (Event) event, new SendCodeModification("CODE", "Firstname Lastname", "email@email.tld", "en"), "http://your-domain.tld/event-page", "Promotional");
        }
    },
    CONFIRMATION_EMAIL("/alfio/templates/confirmation-email", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },
    CONFIRMATION_EMAIL_SUBSCRIPTION("/alfio/templates/confirmation-email-subscription", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            var model = prepareSampleDataForConfirmationEmail(organization, (Event) event);
            model.put("pin", "ABCDE");
            model.put("subscriptionId", UUID.randomUUID().toString());
            model.put("includePin", true);
            model.put("fullName", "Firstname Lastname");
            return model;
        }
    },
    OFFLINE_RESERVATION_EXPIRED_EMAIL("/alfio/templates/offline-reservation-expired-email-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },
    CHARGE_ATTEMPT_FAILED_EMAIL("/alfio/templates/charge-failed-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForChargeFailed(organization, (Event) event);
        }
    },
    CHARGE_ATTEMPT_FAILED_EMAIL_FOR_ORGANIZER("/alfio/templates/charge-failed-organizer-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForChargeFailed(organization, (Event) event);
        }
    },
    CREDIT_NOTE_ISSUED_EMAIL("/alfio/templates/credit-note-issued-email-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },
    OFFLINE_RESERVATION_EXPIRING_EMAIL_FOR_ORGANIZER("/alfio/templates/offline-reservation-expiring-email-for-organizer", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleModelForOfflineReservationExpiringEmailForOrganizer((Event) event);
        }
    },
    OFFLINE_PAYMENT_MATCHES_FOUND("/alfio/templates/offline-payment-matches-found-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return Map.of(
                "matchingCount", 3,
                "eventName", event.getDisplayName(),
                "pendingReviewMatches", true,
                "pendingReview", List.of(UUID.randomUUID().toString()),
                "automaticApprovedMatches", true,
                "automaticApproved", List.of(UUID.randomUUID().toString()),
                "automaticApprovalErrors", true,
                "approvalErrors", List.of(UUID.randomUUID().toString())
            );
        }
    },
    REMINDER_EMAIL("/alfio/templates/reminder-email-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },
    REMINDER_TICKET_ADDITIONAL_INFO("/alfio/templates/reminder-ticket-additional-info.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareModelForReminderTicketAdditionalInfo(organization, (Event) event, sampleTicket(event.getZoneId()), "http://your-domain.tld/ticket-url");
        }
    },
    REMINDER_TICKETS_ASSIGNMENT_EMAIL("/alfio/templates/reminder-tickets-assignment-email-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return prepareSampleDataForConfirmationEmail(organization, (Event) event);
        }
    },


    TICKET_EMAIL("/alfio/templates/ticket-email", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            Event event = (Event) purchaseContext;
            var now = event.now(ClockProvider.clock());
            TicketCategory ticketCategory = new TicketCategory(0, now, now, 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null, "CHF", 0, null, TicketCategory.TicketAccessType.INHERIT);
            return buildModelForTicketEmail(organization, event, sampleTicketReservation(event.getZoneId()), "http://your-domain.tld", "http://your-domain.tld/ticket-url", "http://your-domain.tld/calendar-url", sampleTicket(event.getZoneId()), ticketCategory, Map.of());
        }
    },

    TICKET_EMAIL_FOR_ONLINE_EVENT("/alfio/templates/ticket-email-online", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            Event event = (Event) purchaseContext;
            var now = event.now(ClockProvider.clock());
            TicketCategory ticketCategory = new TicketCategory(0, now, now, 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null, "CHF", 0, null, TicketCategory.TicketAccessType.INHERIT);
            return buildModelForTicketEmail(organization, event, sampleTicketReservation(event.getZoneId()), "http://your-domain.tld", "http://your-domain.tld/ticket-url", "http://your-domain.tld/calendar-url", sampleTicket(event.getZoneId()), ticketCategory, Map.of("onlineCheckInUrl", "https://your-domain.tld/check-in", "prerequisites", "An internet connection is required to join the event"));
        }
    },

    TICKET_HAS_CHANGED_OWNER("/alfio/templates/ticket-has-changed-owner-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return buildModelForTicketHasChangedOwner(organization, (Event) event, sampleTicket(event.getZoneId()), sampleTicket("NewFirstname", "NewLastname", "newemail@email.tld", event.getZoneId()), "http://your-domain.tld/ticket-url");
        }
    },

    TICKET_HAS_BEEN_CANCELLED("/alfio/templates/ticket-has-been-cancelled-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return buildModelForTicketHasBeenCancelled(organization, (Event) event, sampleTicket(event.getZoneId()));
        }
    },

    TICKET_HAS_BEEN_CANCELLED_ADMIN("/alfio/templates/ticket-has-been-cancelled-admin-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return buildModelForTicketHasBeenCancelledAdmin(organization, (Event) event, sampleTicket(event.getZoneId()), "Category", Collections.emptyList(), asi -> Optional.empty());
        }
    },


    TICKET_PDF("/alfio/templates/ticket.ms", APPLICATION_PDF_VALUE, TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            Event event = (Event) purchaseContext;
            var now = event.now(ClockProvider.clock());
            TicketCategory ticketCategory = new TicketCategory(0, now, now, 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null, "CHF", 0, null, TicketCategory.TicketAccessType.INHERIT);
            var ticketWithMetadata = TicketWithMetadataAttributes.build(sampleTicket(event.getZoneId()), null);
            return buildModelForTicketPDF(organization, event, sampleTicketReservation(event.getZoneId()), ticketCategory, ticketWithMetadata, imageData, "ABCD", Collections.emptyMap(), List.of());
        }
    },
    RECEIPT_PDF("/alfio/templates/receipt.ms", APPLICATION_PDF_VALUE, TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return sampleBillingDocument(imageData, organization, (Event) event);
        }
    },

    INVOICE_PDF("/alfio/templates/invoice.ms", APPLICATION_PDF_VALUE, TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return sampleBillingDocument(imageData, organization, (Event) event);
        }
    },

    CREDIT_NOTE_PDF("/alfio/templates/credit-note.ms", APPLICATION_PDF_VALUE, TemplateManager.TemplateOutput.HTML) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return sampleBillingDocument(imageData, organization, (Event) event);
        }
    },
    SUBSCRIPTION_PDF("/alfio/templates/subscription.ms", APPLICATION_PDF_VALUE, TemplateManager.TemplateOutput.HTML, PurchaseContextType.subscription) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            var subscriptionDescriptor = (SubscriptionDescriptor) purchaseContext;
            var zoneId = subscriptionDescriptor.getZoneId();
            var subscription = new Subscription(UUID.randomUUID(),
                "firstName",
                "lastName",
                "email@example.org",
                subscriptionDescriptor.getId(),
                RESERVATION_ID_VALUE,
                organization.getId(),
                ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)),
                ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)),
                100,
                0,
                "CHF",
                AllocationStatus.ACQUIRED,
                1,
                ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)),
                ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)).plusDays(1),
                ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)),
                subscriptionDescriptor.getTimeZone()
            );
            var subscriptionMetadata = new SubscriptionMetadata(Map.of("key", "value"), SubscriptionConfiguration.defaultConfiguration());
            return buildModelForSubscriptionPDF(subscription, subscriptionDescriptor, organization, subscriptionMetadata, imageData, RESERVATION_ID_VALUE, Locale.ENGLISH, sampleTicketReservation(zoneId), List.of());
        }
    },

    WAITING_QUEUE_JOINED("/alfio/templates/waiting-queue-joined.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
            return buildModelForWaitingQueueJoined(organization, (Event) event, new CustomerName("Firstname Lastname", "Firstname", "Lastname", event.mustUseFirstAndLastName()));
        }
    },
    WAITING_QUEUE_RESERVATION_EMAIL("/alfio/templates/waiting-queue-reservation-email-txt.ms", TEXT_PLAIN_VALUE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            Event event = (Event) purchaseContext;
            var clock = ClockProvider.clock().withZone(event.getZoneId());
            WaitingQueueSubscription subscription = new WaitingQueueSubscription(0, ZonedDateTime.now(clock), event.getId(), "ACQUIRED", "Firstname Lastname", "Firstname", "Lastname",
                "email@email.tld", RESERVATION_ID_VALUE, "en", null, WaitingQueueSubscription.Type.PRE_SALES);
            return buildModelForWaitingQueueReservationEmail(organization, event, subscription, "http://your-domain.tld/reservation-url", ZonedDateTime.now(clock));
        }
    },
    CUSTOM_MESSAGE("/alfio/templates/custom-message", TemplateResource.MULTIPART_ALTERNATIVE_MIMETYPE, TemplateManager.TemplateOutput.TEXT) {
        @Override
        public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext purchaseContext, Optional<ImageData> imageData) {
            var now = purchaseContext.now(ClockProvider.clock());
            var event = (Event) purchaseContext;
            TicketCategory ticketCategory = new TicketCategory(0, now, now, 42, "Ticket", false, TicketCategory.Status.ACTIVE, event.getId(), false, 1000, null, null, null, null, null, "CHF", 0, null, TicketCategory.TicketAccessType.INHERIT);
            var model = buildModelForTicketEmail(organization, event, sampleTicketReservation(event.getZoneId()), "http://your-domain.tld", "http://your-domain.tld/ticket-url", "http://your-domain.tld/calendar-url", sampleTicket(event.getZoneId()), ticketCategory, Map.of());
            model.put("message", "This is your message");
            return model;
        }
    },;

    public static final String MULTIPART_ALTERNATIVE_MIMETYPE = "multipart/alternative";

    private static final String PLAIN_TEMPLATE_SUFFIX = "-txt.ms";

    private static final String HTML_TEMPLATE_SUFFIX = "-html.ms";
    private static final String TICKET_KEY = "ticket";
    private static final String RESERVATION_ID = "reservationId";
    private static final String RESERVATION_ID_VALUE = "597e7e7b-c514-4dcb-be8c-46cf7fe2c36e";


    private final String classPathUrl;
    // currently not used, might be removed in the future
    private final boolean overridable;
    private final String renderedContentType;
    private final TemplateManager.TemplateOutput templateOutput;
    /**
     * PurchaseContextType used for preview
     */
    private final PurchaseContextType purchaseContextType;

    TemplateResource(String classPathUrl, String renderedContentType, TemplateManager.TemplateOutput templateOutput) {
        this(classPathUrl, renderedContentType, templateOutput, PurchaseContextType.event);
    }
    TemplateResource(String classPathUrl, String renderedContentType, TemplateManager.TemplateOutput templateOutput, PurchaseContextType purchaseContextType) {
        this.classPathUrl = classPathUrl;
        this.overridable = true;
        this.renderedContentType = renderedContentType;
        this.templateOutput = templateOutput;
        this.purchaseContextType = purchaseContextType;
    }


    public String getSavedName(Locale locale) {
        return name() + "_" + locale.getLanguage() + ".ms";
    }

    public boolean overridable() {
        return overridable;
    }

    public String classPath() {
        return isMultipart() ? classPathUrl + PLAIN_TEMPLATE_SUFFIX : classPathUrl;
    }

    public String htmlClassPath() {
    	return isMultipart() ? classPathUrl + HTML_TEMPLATE_SUFFIX : null;
    }

    public boolean isMultipart() {
    	return MULTIPART_ALTERNATIVE_MIMETYPE.equals(renderedContentType);
    }

    public String getRenderedContentType() {
        return renderedContentType;
    }

    public TemplateManager.TemplateOutput getTemplateOutput() {
        return templateOutput;
    }

    public PurchaseContextType getPurchaseContextType() {
        return purchaseContextType;
    }

    public Map<String, Object> prepareSampleModel(Organization organization, PurchaseContext event, Optional<ImageData> imageData) {
        return Collections.emptyMap();
    }

    private static Ticket sampleTicket(ZoneId zoneId) {
        return sampleTicket("Firstname", "Lastname", "email@email.tld", zoneId);
    }

    private static Map<String, Object> sampleBillingDocument(Optional<ImageData> imageData, Organization organization, Event event) {
        Map<String, Object> model = prepareSampleDataForConfirmationEmail(organization, event);
        imageData.ifPresent(iData -> {
            model.put("eventImage", iData.getEventImage());
            model.put("imageWidth", iData.getImageWidth());
            model.put("imageHeight", iData.getImageHeight());
        });
        return model;
    }

    private static TicketCategory sampleCategory(ZoneId zoneId) {
        var clock = ClockProvider.clock().withZone(zoneId);
        return new TicketCategory(0, ZonedDateTime.now(clock).minusDays(1), ZonedDateTime.now(clock).plusDays(1), 100, "test category", false, TicketCategory.Status.ACTIVE,
            0, true, 100, null, null, null, null, null, "CHF", 0, null, TicketCategory.TicketAccessType.INHERIT);
    }

    private static Ticket sampleTicket(String firstName, String lastName, String email, ZoneId zoneId) {
        return new Ticket(0, RESERVATION_ID_VALUE, ZonedDateTime.now(ClockProvider.clock().withZone(zoneId)), 0, "ACQUIRED", 0,
            RESERVATION_ID_VALUE, firstName + " " + lastName, firstName, lastName, email, false, "en",
            1000, 1000, 80, 0, null, "CHF", List.of(), null, PriceContainer.VatStatus.INCLUDED);
    }

    private static TicketReservation sampleTicketReservation(ZoneId zoneId) {
        var clock = ClockProvider.clock().withZone(zoneId);
        return new TicketReservation(RESERVATION_ID_VALUE, new Date(), TicketReservation.TicketReservationStatus.COMPLETE,
            "Firstname Lastname", "FirstName", "Lastname", "email@email.tld", "billing address", ZonedDateTime.now(clock), ZonedDateTime.now(clock),
            PaymentProxy.STRIPE, true, null, false, "en", false, null, null, null, "123456",
            "CH", false, new BigDecimal("8.00"), true,
            ZonedDateTime.now(clock).minusMinutes(1), "PO-1234", ZonedDateTime.now(clock), 10000, 10800, 800, 0, "CHF");
    }

    private static Map<String, Object> prepareSampleDataForConfirmationEmail(Organization organization, Event event) {
        TicketReservation reservation = sampleTicketReservation(event.getZoneId());
        Optional<String> vat = Optional.of("VAT-NR");
        List<TicketWithCategory> tickets = Collections.singletonList(new TicketWithCategory(sampleTicket(event.getZoneId()), sampleCategory(event.getZoneId())));
        OrderSummary orderSummary = new OrderSummary(new TotalPrice(1000, 80, 0, 0, "CHF"),
            List.of(new SummaryRow("Ticket", "10.00", "9.20", 1, "9.20", "9.20", 1000, SummaryRow.SummaryType.TICKET, null, PriceContainer.VatStatus.INCLUDED)), false, "10.00", "0.80", false, false, false, "8", PriceContainer.VatStatus.INCLUDED, "1.00");
        String baseUrl = "http://your-domain.tld";
        String reservationUrl = baseUrl + "/reservation-url/";
        String reservationShortId = "597e7e7b";
        return prepareModelForConfirmationEmail(organization, event, reservation, vat, tickets, orderSummary, baseUrl, reservationUrl, reservationShortId, Optional.of("My Invoice\nAddress"), Optional.empty(), Optional.empty(), Map.of());
    }

    private static Map<String, Object> prepareSampleDataForChargeFailed(Organization organization, Event event) {
        TicketReservation reservation = sampleTicketReservation(event.getZoneId());
        return Map.of(
            RESERVATION_ID, reservation.getId().substring(0, 8),
            "reservationCancelled", true,
            "reservation", reservation,
            "eventName", event.getDisplayName(),
            "provider", PaymentMethod.CREDIT_CARD.name(),
            "reason", "this is the reason from the provider",
            "reservationUrl", "http://your-domain.tld/reservation-url/",
            "organization", organization
        );
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
                                                                       PurchaseContext purchaseContext,
                                                                       TicketReservation reservation,
                                                                       Optional<String> vat,
                                                                       List<TicketWithCategory> tickets,
                                                                       OrderSummary orderSummary,
                                                                       String baseUrl,
                                                                       String reservationUrl,
                                                                       String reservationShortID,
                                                                       Optional<String> invoiceAddress,
                                                                       Optional<String> bankAccountNr,
                                                                       Optional<String> bankAccountOwner,
                                                                       Map<String, Object> additionalModelObjects) {
        Map<String, Object> model = new HashMap<>(additionalModelObjects);
        model.put("organization", organization);
        model.put("event", purchaseContext.event().orElse(null));
        model.put("purchaseContext", purchaseContext);
        model.put("purchaseContextTitle", purchaseContext.getTitle().get(reservation.getUserLanguage()));
        model.put("ticketReservation", reservation);
        model.put("hasVat", vat.isPresent());
        model.put("vatNr", vat.orElse(""));
        model.put("tickets", tickets);
        model.put("orderSummary", orderSummary);
        model.put("baseUrl", baseUrl);
        model.put("reservationUrl", reservationUrl);
        model.put("locale", reservation.getUserLanguage());

        model.put("hasRefund", StringUtils.isNotEmpty(orderSummary.getRefundedAmount()));

        var zoneId = purchaseContext.getZoneId();
        var clock = ClockProvider.clock().withZone(zoneId);
        ZonedDateTime creationTimestamp = ObjectUtils.firstNonNull(reservation.getRegistrationTimestamp(), reservation.getConfirmationTimestamp(), reservation.getCreationTimestamp(), ZonedDateTime.now(clock));
        model.put("confirmationDate", creationTimestamp.withZoneSameInstant(zoneId));
        model.put("now", ZonedDateTime.now(clock));

        if (reservation.getValidity() != null) {
            model.put("expirationDate", ZonedDateTime.ofInstant(reservation.getValidity().toInstant(), zoneId));
        }

        model.put("reservationShortID", reservationShortID);

        model.put("hasInvoiceAddress", invoiceAddress.isPresent());
        invoiceAddress.ifPresent(addr -> {
            model.put("invoiceAddress", StringUtils.replace(addr, "\n", ", "));
            model.put("invoiceAddressAsList", Arrays.asList(StringUtils.split(addr, '\n')));
        });

        model.put("hasBankAccountNr", bankAccountNr.isPresent());
        bankAccountNr.ifPresent(nr -> model.put("bankAccountNr", nr));

        model.put("isOfflinePayment", reservation.getStatus() == TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);
        model.put("hasCustomerReference", StringUtils.isNotBlank(reservation.getCustomerReference()));
        model.put("paymentReason", reservationShortID);
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

    private static Map<String, Object> prepareSampleModelForOfflineReservationExpiringEmailForOrganizer(Event event) {
        Map<String, Object> model = new HashMap<>();
        model.put("eventName", event.getDisplayName());
        model.put("ticketReservations", Collections.singletonList(new TicketReservationInfo("id", null, "Firstname", "Lastname", "email@email.email", 1000, "EUR", 42, new Date())));
        model.put("baseUrl", "http://base-url/");
        model.put("eventShortName", event.getShortName());
        return model;
    }

    // used by SEND_RESERVED_CODE
    public static Map<String, Object> prepareModelForSendReservedCode(Organization organization,
                                                                      Event event,
                                                                      SendCodeModification m,
                                                                      String eventPageUrl,
                                                                      String promoCodeLabel) {
        Map<String, Object> model = new HashMap<>();
        model.put("code", m.getCode());
        model.put("event", event);
        model.put("organization", organization);
        model.put("eventPage", eventPageUrl);
        model.put("assignee", m.getAssignee());
        model.put("promoCodeDescription", promoCodeLabel);
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
                                                               String baseUrl,
                                                               String ticketURL,
                                                               String calendarURL,
                                                               Ticket ticket,
                                                               TicketCategory ticketCategory,
                                                               Map<String, Object> additionalOptions) {
        Map<String, Object> model = new HashMap<>(additionalOptions);
        model.put("organization", organization);
        model.put("event", event);
        model.put("ticketReservation", ticketReservation);
        model.put("baseUrl", baseUrl);
        model.put("ticketUrl", ticketURL);
        model.put(TICKET_KEY, ticket);
        model.put("googleCalendarUrl", calendarURL);
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
        emailModel.put(TICKET_KEY, oldTicket);
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
        model.put(TICKET_KEY, ticket);
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
                data.put("amount", MonetaryUtil.centsToUnit(asi.getFinalPriceCts(), asi.getCurrencyCode()).toString() + event.getCurrency());
                data.put("id", asi.getId());
                return data;
            }).collect(Collectors.toList()));
        return model;
    }

    // used by TICKET_PDF
    public static Map<String, Object> buildModelForTicketPDF(Organization organization,
                                                             Event event,
                                                             TicketReservation ticketReservation,
                                                             TicketCategory ticketCategory,
                                                             TicketWithMetadataAttributes ticketWithMetadata,
                                                             Optional<ImageData> imageData,
                                                             String reservationId,
                                                             Map<String, String> additionalFields,
                                                             List<AdditionalServiceWithData> additionalServicesWithData) {
        String qrCodeText = ticketWithMetadata.getTicket().ticketCode(event.getPrivateKey(), event.supportsQRCodeCaseInsensitive());
        //
        Map<String, Object> model = new HashMap<>();
        model.put(TICKET_KEY, ticketWithMetadata.getTicket());
        model.put("reservation", ticketReservation);
        model.put("ticketCategory", ticketCategory);
        model.put("event", event);
        model.put("organization", organization);
        model.put(RESERVATION_ID, reservationId);
        model.put(ADDITIONAL_FIELDS_KEY, additionalFields);
        model.put(METADATA_ATTRIBUTES_KEY, ticketWithMetadata.getAttributes());
        fillTicketValidity(event, ticketCategory, model);

        model.put("qrCodeDataUri", "data:image/png;base64," + Base64.getEncoder().encodeToString(createQRCode(qrCodeText)));

        imageData.ifPresent(iData -> {
            model.put("eventImage", iData.getEventImage());
            model.put("imageWidth", iData.getImageWidth());
            model.put("imageHeight", iData.getImageHeight());
        });

        model.put("deskPaymentRequired", Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired());
        fillAdditionalServices(ticketWithMetadata, additionalServicesWithData, model);
        return model;
    }

    private static void fillAdditionalServices(TicketWithMetadataAttributes ticketWithMetadata, List<AdditionalServiceWithData> additionalServicesWithData, Map<String, Object> model) {
        model.put("hasAdditionalServices", !additionalServicesWithData.isEmpty());
        var byServiceId = additionalServicesWithData.stream().collect(Collectors.groupingBy(AdditionalServiceWithData::getServiceId));
        var additionalServices = byServiceId.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                var first = entry.getValue().get(0);
                var userLanguage = ticketWithMetadata.getTicket().getUserLanguage();
                return new GroupedAdditionalServiceWithData(
                    entry.getValue().size(),
                    first.getTitle().get(userLanguage),
                    entry.getValue().stream()
                        .flatMap(asw -> asw.getTicketFieldConfiguration().stream()
                            .filter(tfc -> StringUtils.isNotBlank(tfc.getValue()))
                            .map(tfc -> {
                                String value = tfc.getRestrictedValues().stream().filter(rv -> rv.equals(tfc.getValue()))
                                    .findFirst()
                                    .orElse(tfc.getValue());
                            return new FieldNameAndValue(tfc.getDescription().get(userLanguage).getLabel(), value);
                        })).collect(Collectors.toList())
                );
            }).collect(Collectors.toList());
        model.put("additionalServices", additionalServices);
    }

    public static Map<String, Object> buildModelForSubscriptionPDF(Subscription subscription,
                                                                   SubscriptionDescriptor subscriptionDescriptor,
                                                                   Organization organization,
                                                                   SubscriptionMetadata metadata,
                                                                   Optional<ImageData> imageData,
                                                                   String reservationId,
                                                                   Locale locale,
                                                                   TicketReservation reservation,
                                                                   List<FieldConfigurationDescriptionAndValue> fields) {
        Map<String, Object> model = new HashMap<>();
        model.put("title", subscriptionDescriptor.getLocalizedTitle(locale));
        model.put("validityTypeNotSet", subscriptionDescriptor.getValidityType() == SubscriptionValidityType.NOT_SET);
        model.put("validityTypeStandard", subscriptionDescriptor.getValidityType() == SubscriptionValidityType.STANDARD);
        model.put("validityTypeCustom", subscriptionDescriptor.getValidityType() == SubscriptionValidityType.CUSTOM);
        model.put("subscription", subscription);
        model.put(SUBSCRIPTION_DESCRIPTOR_ATTRIBUTE, subscriptionDescriptor);
        model.put("organization", organization);
        model.put("reservation", reservation);
        model.put(RESERVATION_ID, reservationId);
        model.put(METADATA_ATTRIBUTES_KEY, metadata.getProperties());
        model.put("displayPin", metadata.getConfiguration().isDisplayPin());
        imageData.ifPresent(iData -> {
            model.put("logo", iData.getEventImage());
            model.put("imageWidth", iData.getImageWidth());
            model.put("imageHeight", iData.getImageHeight());
        });
        model.put(ADDITIONAL_FIELDS_KEY, fields.stream().collect(Collectors.toMap(FieldConfigurationDescriptionAndValue::getName, FieldConfigurationDescriptionAndValue::getValueDescription)));
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

    static class GroupedAdditionalServiceWithData {

        private final int count;
        private final String title;
        private final List<FieldNameAndValue> fields;

        GroupedAdditionalServiceWithData(int count, String title, List<FieldNameAndValue> fields) {
            this.count = count;
            this.title = title;
            this.fields = List.copyOf(requireNonNullElse(fields, List.of()));
        }

        public int getCount() {
            return count;
        }

        public String getTitle() {
            return title;
        }

        public List<FieldNameAndValue> getFields() {
            return fields;
        }
    }
}
