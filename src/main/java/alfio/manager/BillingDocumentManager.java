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
package alfio.manager;

import alfio.manager.payment.PaymentSpecification;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import alfio.util.TemplateResource;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;

import static alfio.model.Audit.EntityType.RESERVATION;
import static alfio.model.Audit.EventType.EXTERNAL_CREDIT_NOTE_NUMBER;
import static alfio.model.Audit.EventType.EXTERNAL_INVOICE_NUMBER;
import static alfio.model.BillingDocument.Type.*;
import static alfio.model.TicketReservation.TicketReservationStatus.CANCELLED;
import static alfio.model.TicketReservation.TicketReservationStatus.PENDING;
import static alfio.model.system.ConfigurationKeys.*;
import static alfio.util.ReservationUtil.collectTicketsWithCategory;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@Component
@AllArgsConstructor
@Log4j2
public class BillingDocumentManager {
    static final String CREDIT_NOTE_NUMBER = "creditNoteNumber";
    private static final String APPLICATION_PDF = "application/pdf";
    private final BillingDocumentRepository billingDocumentRepository;
    private final Json json;
    private final ConfigurationManager configurationManager;
    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AuditingRepository auditingRepository;
    private final TicketReservationRepository ticketReservationRepository;
    private final ClockProvider clockProvider;
    private final ExtensionManager extensionManager;
    private final InvoiceSequencesRepository invoiceSequencesRepository;


    public Optional<ZonedDateTime> findFirstInvoiceDate(int eventId) {
        return billingDocumentRepository.findFirstInvoiceGenerationDate(eventId);
    }

    public List<Integer> findMatchingInvoiceIds(Integer eventId, ZonedDateTime from, ZonedDateTime to) {
        return billingDocumentRepository.findMatchingInvoiceIds(eventId, from, to);
    }

    static boolean mustGenerateBillingDocument(OrderSummary summary, TicketReservation ticketReservation) {
        return !summary.getFree() && (!summary.getNotYetPaid() || (summary.getWaitingForPayment() && ticketReservation.isInvoiceRequested()));
    }

    public List<Mailer.Attachment> generateBillingDocumentAttachment(PurchaseContext purchaseContext,
                                                              TicketReservation ticketReservation,
                                                              Locale language,
                                                              BillingDocument.Type documentType,
                                                              String username, 
                                                              OrderSummary orderSummary) {
        Map<String, String> model = new HashMap<>();
        model.put("reservationId", ticketReservation.getId());
        model.put("eventId", purchaseContext.event().map(ev -> Integer.toString(ev.getId())).orElse(null));
        model.put("language", json.asJsonString(language));
        model.put("reservationEmailModel", json.asJsonString(internalGetOrCreate(purchaseContext, ticketReservation, username, orderSummary).getModel()));
        switch (documentType) {
            case INVOICE:
                return Collections.singletonList(new Mailer.Attachment("invoice.pdf", null, APPLICATION_PDF, model, Mailer.AttachmentIdentifier.INVOICE_PDF));
            case RECEIPT:
                return Collections.singletonList(new Mailer.Attachment("receipt.pdf", null, APPLICATION_PDF, model, Mailer.AttachmentIdentifier.RECEIPT_PDF));
            case CREDIT_NOTE:
                return Collections.singletonList(new Mailer.Attachment("credit-note.pdf", null, APPLICATION_PDF, model, Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF));
            default:
                throw new IllegalStateException(documentType+" is not supported");
        }
    }

    @Transactional
    public void ensureBillingDocumentIsPresent(PurchaseContext purchaseContext, TicketReservation reservation, String username, Supplier<OrderSummary> orderSummarySupplier) {
        if(reservation.getStatus() == PENDING || reservation.getStatus() == CANCELLED) {
            return;
        }
        var orderSummary = orderSummarySupplier.get();
        if(mustGenerateBillingDocument(orderSummary, reservation)) {
            getOrCreateBillingDocument(purchaseContext, reservation, username, orderSummary);
        }
    }

    @Transactional
    public BillingDocument createBillingDocument(PurchaseContext purchaseContext, TicketReservation reservation, String username, OrderSummary orderSummary) {
        return createBillingDocument(purchaseContext, reservation, username, reservation.getHasInvoiceNumber() ? INVOICE : RECEIPT, orderSummary);
    }

    BillingDocument createBillingDocument(PurchaseContext purchaseContext, TicketReservation reservation, String username, BillingDocument.Type type, OrderSummary orderSummary) {
        Map<String, Object> model = prepareModelForBillingDocument(purchaseContext, reservation, orderSummary, type);
        String number;
        if(type == INVOICE) {
            number = reservation.getInvoiceNumber();
        } else if (type == CREDIT_NOTE) {
            number = (String) model.get(CREDIT_NOTE_NUMBER);
        } else {
            number = UUID.randomUUID().toString();
        }
        var eventId = purchaseContext.event().map(Event::getId).orElse(null);
        AffectedRowCountAndKey<Long> doc = billingDocumentRepository.insert(eventId, reservation.getId(), number, type, json.asJsonString(model), purchaseContext.now(clockProvider), purchaseContext.getOrganizationId());
        log.trace("billing document #{} created", doc.getKey());
        auditingRepository.insert(reservation.getId(), userRepository.nullSafeFindIdByUserName(username).orElse(null), purchaseContext, Audit.EventType.BILLING_DOCUMENT_GENERATED, new Date(), Audit.EntityType.RESERVATION, reservation.getId(), singletonList(singletonMap("documentId", doc.getKey())));
        return billingDocumentRepository.findByIdAndReservationId(doc.getKey(), reservation.getId()).orElseThrow(IllegalStateException::new);
    }

    @Transactional
    public BillingDocument getOrCreateBillingDocument(PurchaseContext purchaseContext, TicketReservation reservation, String username, OrderSummary orderSummary) {
        return internalGetOrCreate(purchaseContext, reservation, username, orderSummary);
    }

    private BillingDocument internalGetOrCreate(PurchaseContext purchaseContext, TicketReservation reservation, String username, OrderSummary orderSummary) {
        Optional<BillingDocument> existing = billingDocumentRepository.findLatestByReservationId(reservation.getId());
        return existing.orElseGet(() -> createBillingDocument(purchaseContext, reservation, username, orderSummary));
    }

    public Optional<BillingDocument> getDocumentById(long id) {
        return billingDocumentRepository.findById(id);
    }

    @Transactional
    public Optional<String> generateInvoiceNumber(PaymentSpecification spec, TotalPrice reservationCost) {
        if(!reservationCost.requiresPayment() || !spec.isInvoiceRequested() || !configurationManager.hasAllConfigurationsForInvoice(spec.getPurchaseContext())) {
            return Optional.empty();
        }

        String reservationId = spec.getReservationId();
        var billingDetails = ticketReservationRepository.getBillingDetailsForReservation(reservationId);
        var optionalInvoiceNumber = extensionManager.handleInvoiceGeneration(spec, reservationCost, billingDetails, Map.of("organization", organizationRepository.getById(spec.getPurchaseContext().getOrganizationId())))
            .flatMap(invoiceGeneration -> Optional.ofNullable(trimToNull(invoiceGeneration.getInvoiceNumber())));

        optionalInvoiceNumber.ifPresent(invoiceNumber -> {
            List<Map<String, Object>> modifications = List.of(Map.of("invoiceNumber", invoiceNumber));
            auditingRepository.insert(reservationId, null, spec.getPurchaseContext(), EXTERNAL_INVOICE_NUMBER, new Date(), RESERVATION, reservationId, modifications);
        });

        return optionalInvoiceNumber.or(() -> {
            int invoiceSequence = invoiceSequencesRepository.lockSequenceForUpdate(spec.getPurchaseContext().getOrganizationId());
            invoiceSequencesRepository.incrementSequenceFor(spec.getPurchaseContext().getOrganizationId());
            return Optional.of(formatDocumentNumber(spec.getPurchaseContext(), invoiceSequence));
        });
    }

    String generateCreditNoteNumber(PurchaseContext purchaseContext, TicketReservation reservation) {

        return extensionManager.handleCreditNoteGeneration(purchaseContext, reservation.getId(), reservation.getInvoiceNumber(), organizationRepository.getById(purchaseContext.getOrganizationId()))
            .map(cng -> {
                var reservationId = reservation.getId();
                var creditNoteNumber = cng.getCreditNoteNumber();
                auditingRepository.insert(reservationId, null, purchaseContext, EXTERNAL_CREDIT_NOTE_NUMBER, new Date(), RESERVATION, reservationId, List.of(Map.of(CREDIT_NOTE_NUMBER, creditNoteNumber)));
                return creditNoteNumber;
            })
            .orElseGet(() -> {
                if (configurationManager.getFor(REUSE_INVOICE_NUMBER_FOR_CREDIT_NOTE, purchaseContext.getConfigurationLevel()).getValueAsBooleanOrDefault()) {
                    return reservation.getInvoiceNumber();
                } else {
                    int creditNoteSequence = invoiceSequencesRepository.lockSequenceForUpdate(purchaseContext.getOrganizationId(), CREDIT_NOTE);
                    invoiceSequencesRepository.incrementSequenceFor(purchaseContext.getOrganizationId(), CREDIT_NOTE);
                    return formatDocumentNumber(purchaseContext, creditNoteSequence);
                }
            });
    }

    private String formatDocumentNumber(PurchaseContext purchaseContext, int sequence) {
        String pattern = configurationManager
            .getFor(ConfigurationKeys.INVOICE_NUMBER_PATTERN, purchaseContext.getConfigurationLevel())
            .getValueOrDefault("%d");
        return String.format(ObjectUtils.firstNonNull(StringUtils.trimToNull(pattern), "%d"), sequence);
    }

    private Map<String, Object> prepareModelForBillingDocument(PurchaseContext purchaseContext, TicketReservation reservation, OrderSummary summary, BillingDocument.Type type) {
        Organization organization = organizationRepository.getById(purchaseContext.getOrganizationId());

        String creditNoteNumber = null;
        if(type == CREDIT_NOTE) {
            // override credit note number
            creditNoteNumber = generateCreditNoteNumber(purchaseContext, reservation);
        }

        var bankingInfo = configurationManager.getFor(Set.of(VAT_NR, INVOICE_ADDRESS, BANK_ACCOUNT_NR, BANK_ACCOUNT_OWNER), purchaseContext.getConfigurationLevel());
        Optional<String> invoiceAddress = bankingInfo.get(INVOICE_ADDRESS).getValue();
        Optional<String> bankAccountNr = bankingInfo.get(BANK_ACCOUNT_NR).getValue();
        Optional<String> bankAccountOwner = bankingInfo.get(BANK_ACCOUNT_OWNER).getValue();
        Optional<String> vat = bankingInfo.get(VAT_NR).getValue();

        Map<Integer, List<Ticket>> ticketsByCategory = ticketRepository.findTicketsInReservation(reservation.getId())
            .stream()
            .collect(groupingBy(Ticket::getCategoryId));
        List<TicketWithCategory> ticketsWithCategory = collectTicketsWithCategory(ticketsByCategory, ticketCategoryRepository);
        var reservationShortId = configurationManager.getShortReservationID(purchaseContext, reservation);
        Map<String, Object> model = TemplateResource.prepareModelForConfirmationEmail(organization, purchaseContext, reservation, vat, ticketsWithCategory, summary, "", "", reservationShortId, invoiceAddress, bankAccountNr, bankAccountOwner, Map.of());
        boolean euBusiness = StringUtils.isNotBlank(reservation.getVatCountryCode()) && StringUtils.isNotBlank(reservation.getVatNr())
            && configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue().contains(reservation.getVatCountryCode())
            && PriceContainer.VatStatus.isVatExempt(reservation.getVatStatus());
        model.put("isEvent", purchaseContext.ofType(PurchaseContextType.event));
        model.put("euBusiness", euBusiness);
        model.put("publicId", configurationManager.getPublicReservationID(purchaseContext, reservation));
        var additionalInfo = ticketReservationRepository.getAdditionalInfo(reservation.getId());
        model.put("invoicingAdditionalInfo", additionalInfo.getInvoicingAdditionalInfo());
        model.put("proforma", !additionalInfo.getInvoicingAdditionalInfo().isEmpty());
        model.put("billingDetails", additionalInfo.getBillingDetails());
        if(type == CREDIT_NOTE) {
            model.put(CREDIT_NOTE_NUMBER, creditNoteNumber);
        }
        return model;
    }
}
