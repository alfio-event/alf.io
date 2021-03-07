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

import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.Mailer;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.extension.CreditNoteGeneration;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;

import static alfio.model.BillingDocument.Type.*;
import static alfio.model.TicketReservation.TicketReservationStatus.CANCELLED;
import static alfio.model.TicketReservation.TicketReservationStatus.PENDING;
import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Component
@AllArgsConstructor
@Log4j2
public class BillingDocumentManager {
    private static final String CREDIT_NOTE_NUMBER = "creditNoteNumber";
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


    public Optional<ZonedDateTime> findFirstInvoiceDate(int eventId) {
        return billingDocumentRepository.findFirstInvoiceGenerationDate(eventId);
    }

    public List<Integer> findMatchingInvoiceIds(Integer eventId, ZonedDateTime from, ZonedDateTime to) {
        return billingDocumentRepository.findMatchingInvoiceIds(eventId, from, to);
    }

    static boolean mustGenerateBillingDocument(OrderSummary summary, TicketReservation ticketReservation) {
        return !summary.getFree() && (!summary.getNotYetPaid() || (summary.getWaitingForPayment() && ticketReservation.isInvoiceRequested()));
    }

    List<Mailer.Attachment> generateBillingDocumentAttachment(PurchaseContext purchaseContext,
                                                              TicketReservation ticketReservation,
                                                              Locale language,
                                                              BillingDocument.Type documentType,
                                                              String username, 
                                                              OrderSummary orderSummary) {
        Map<String, String> model = new HashMap<>();
        model.put("reservationId", ticketReservation.getId());
        model.put("eventId", purchaseContext.event().map(ev -> Integer.toString(ev.getId())).orElse(null));
        model.put("language", json.asJsonString(language));
        model.put("reservationEmailModel", json.asJsonString(getOrCreateBillingDocument(purchaseContext, ticketReservation, username, orderSummary).getModel()));
        switch (documentType) {
            case INVOICE:
                return Collections.singletonList(new Mailer.Attachment("invoice.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.INVOICE_PDF));
            case RECEIPT:
                return Collections.singletonList(new Mailer.Attachment("receipt.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.RECEIPT_PDF));
            case CREDIT_NOTE:
                return Collections.singletonList(new Mailer.Attachment("credit-note.pdf", null, "application/pdf", model, Mailer.AttachmentIdentifier.CREDIT_NOTE_PDF));
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
        Optional<BillingDocument> existing = billingDocumentRepository.findLatestByReservationId(reservation.getId());
        return existing.orElseGet(() -> createBillingDocument(purchaseContext, reservation, username, orderSummary));
    }

    public Optional<BillingDocument> getDocumentById(long id) {
        return billingDocumentRepository.findById(id);
    }

    private Map<String, Object> prepareModelForBillingDocument(PurchaseContext purchaseContext, TicketReservation reservation, OrderSummary summary, BillingDocument.Type type) {
        Organization organization = organizationRepository.getById(purchaseContext.getOrganizationId());

        String creditNoteNumber = reservation.getInvoiceNumber();
        if(type == CREDIT_NOTE) {
            // override credit note number
            creditNoteNumber = extensionManager.handleCreditNoteGeneration(purchaseContext, reservation.getId(), reservation.getInvoiceNumber(), organizationRepository.getById(purchaseContext.getOrganizationId()))
                .map(CreditNoteGeneration::getCreditNoteNumber).orElse(creditNoteNumber);
        }

        var bankingInfo = configurationManager.getFor(Set.of(VAT_NR, INVOICE_ADDRESS, BANK_ACCOUNT_NR, BANK_ACCOUNT_OWNER), purchaseContext.getConfigurationLevel());
        Optional<String> invoiceAddress = bankingInfo.get(INVOICE_ADDRESS).getValue();
        Optional<String> bankAccountNr = bankingInfo.get(BANK_ACCOUNT_NR).getValue();
        Optional<String> bankAccountOwner = bankingInfo.get(BANK_ACCOUNT_OWNER).getValue();
        Optional<String> vat = bankingInfo.get(VAT_NR).getValue();

        Map<Integer, List<Ticket>> ticketsByCategory = ticketRepository.findTicketsInReservation(reservation.getId())
            .stream()
            .collect(groupingBy(Ticket::getCategoryId));
        final List<TicketWithCategory> ticketsWithCategory;
        if(!ticketsByCategory.isEmpty()) {
            ticketsWithCategory = ticketCategoryRepository.findByIds(ticketsByCategory.keySet())
                .stream()
                .flatMap(tc -> ticketsByCategory.get(tc.getId()).stream().map(t -> new TicketWithCategory(t, tc)))
                .collect(toList());
        } else {
            ticketsWithCategory = Collections.emptyList();
        }
        Map<String, Object> model = TemplateResource.prepareModelForConfirmationEmail(organization, purchaseContext, reservation, vat, ticketsWithCategory, summary, "", "", "", invoiceAddress, bankAccountNr, bankAccountOwner, Map.of());
        boolean euBusiness = StringUtils.isNotBlank(reservation.getVatCountryCode()) && StringUtils.isNotBlank(reservation.getVatNr())
            && configurationManager.getForSystem(ConfigurationKeys.EU_COUNTRIES_LIST).getRequiredValue().contains(reservation.getVatCountryCode())
            && PriceContainer.VatStatus.isVatExempt(reservation.getVatStatus());
        model.put("isEvent", purchaseContext.getType() == PurchaseContextType.event);
        model.put("euBusiness", euBusiness);
        model.put("publicId", configurationManager.getPublicReservationID(purchaseContext, reservation));
        var additionalInfo = ticketReservationRepository.getAdditionalInfo(reservation.getId());
        model.put("invoicingAdditionalInfo", additionalInfo.getInvoicingAdditionalInfo());
        model.put("billingDetails", additionalInfo.getBillingDetails());
        if(type == CREDIT_NOTE) {
            model.put(CREDIT_NOTE_NUMBER, creditNoteNumber);
        }
        return model;
    }
}
