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

import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.Json;
import alfio.util.TemplateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TicketReservationManagerUnitTest {
    private static final String TICKET_RESERVATION_ID = "abcdef";

    private TicketReservationManager manager;
    
    private PromoCodeDiscountRepository promoCodeDiscountRepository;
    private TicketRepository ticketRepository;
    private TicketReservationRepository ticketReservationRepository;
    private EventRepository eventRepository;
    private TicketReservation reservation;
    private Event event;
    private Ticket ticket;
    private TicketCategory ticketCategory;
    private OrganizationRepository organizationRepository;
    private TicketCategoryRepository ticketCategoryRepository;
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    private ConfigurationManager configurationManager;
    private PaymentManager paymentManager;
    private SpecialPriceRepository specialPriceRepository;
    private TransactionRepository transactionRepository;
    private NotificationManager notificationManager;
    private MessageSource messageSource;
    private MessageSourceManager messageSourceManager;
    private TemplateManager templateManager;
    private PlatformTransactionManager transactionManager;
    private WaitingQueueManager waitingQueueManager;
    private TicketFieldRepository ticketFieldRepository;
    private AdditionalServiceRepository additionalServiceRepository;
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    private AdditionalServiceTextRepository additionalServiceTextRepository;
    private InvoiceSequencesRepository invoiceSequencesRepository;
    private AuditingRepository auditingRepository;
    private UserRepository userRepository;
    private ExtensionManager extensionManager;
    private GroupManager groupManager;
    private Json json;

    @BeforeEach
    public void setUp() {
        promoCodeDiscountRepository = mock(PromoCodeDiscountRepository.class);
        ticketRepository = mock(TicketRepository.class);
        ticketReservationRepository = mock(TicketReservationRepository.class);
        eventRepository = mock(EventRepository.class);
        reservation = mock(TicketReservation.class);
        event = mock(Event.class);
        when(event.getCurrency()).thenReturn("CHF");
        ticket = mock(Ticket.class);
        when(ticket.getCurrencyCode()).thenReturn("CHF");
        ticketCategory = mock(TicketCategory.class);
        organizationRepository = mock(OrganizationRepository.class);
        ticketCategoryRepository = mock(TicketCategoryRepository.class);
        ticketCategoryDescriptionRepository = mock(TicketCategoryDescriptionRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        paymentManager = mock(PaymentManager.class);
        specialPriceRepository = mock(SpecialPriceRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        notificationManager = mock(NotificationManager.class);
        messageSource = mock(MessageSource.class);
        messageSourceManager = mock(MessageSourceManager.class);
        templateManager = mock(TemplateManager.class);
        transactionManager = mock(PlatformTransactionManager.class);
        waitingQueueManager = mock(WaitingQueueManager.class);
        ticketFieldRepository = mock(TicketFieldRepository.class);
        additionalServiceRepository = mock(AdditionalServiceRepository.class);
        additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        additionalServiceTextRepository = mock(AdditionalServiceTextRepository.class);
        invoiceSequencesRepository = mock(InvoiceSequencesRepository.class);
        auditingRepository = mock(AuditingRepository.class);
        userRepository = mock(UserRepository.class);
        extensionManager = mock(ExtensionManager.class);
        groupManager = mock(GroupManager.class);
        BillingDocumentRepository billingDocumentRepository = mock(BillingDocumentRepository.class);
        json = mock(Json.class);

        when(messageSourceManager.getMessageSourceForEvent(any())).thenReturn(messageSource);
        when(messageSourceManager.getRootMessageSource()).thenReturn(messageSource);

        manager = new TicketReservationManager(eventRepository,
            organizationRepository,
            ticketRepository,
            ticketReservationRepository,
            ticketCategoryRepository,
            ticketCategoryDescriptionRepository,
            configurationManager,
            paymentManager,
            promoCodeDiscountRepository,
            specialPriceRepository,
            transactionRepository,
            notificationManager,
            messageSourceManager,
            templateManager,
            transactionManager,
            waitingQueueManager,
            ticketFieldRepository,
            additionalServiceRepository,
            additionalServiceItemRepository,
            additionalServiceTextRepository,
            invoiceSequencesRepository,
            auditingRepository,
            userRepository,
            extensionManager,
            mock(TicketSearchRepository.class),
            groupManager,
            billingDocumentRepository,
            mock(NamedParameterJdbcTemplate.class),
            json);

    }

    @Test
    public void calcReservationCostOnlyTickets() throws Exception {
        when(event.isVatIncluded()).thenReturn(true, false);
        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED, PriceContainer.VatStatus.NOT_INCLUDED);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
        when(eventRepository.findByReservationId(eq(TICKET_RESERVATION_ID))).thenReturn(event);
        when(ticketReservationRepository.findReservationById(eq(TICKET_RESERVATION_ID))).thenReturn(reservation);
        when(reservation.getId()).thenReturn(TICKET_RESERVATION_ID);
        when(ticket.getSrcPriceCts()).thenReturn(10);
        when(ticketRepository.findTicketsInReservation(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());

        TotalPrice included = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(10, included.getPriceWithVAT());
        assertEquals(1, included.getVAT());

        TotalPrice notIncluded = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(11, notIncluded.getPriceWithVAT());
        assertEquals(1, notIncluded.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatIncludedInherited() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 10, 10);
        //first: event price vat included, additional service VAT inherited
        TotalPrice first = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(20, first.getPriceWithVAT());
        assertEquals(2, first.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatIncludedASNoVat() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 10, 10);
        //second: event price vat included, additional service VAT n/a
        TotalPrice second = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(20, second.getPriceWithVAT());
        assertEquals(1, second.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatNotIncludedASInherited() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 10, 10);
        //third: event price vat not included, additional service VAT inherited
        TotalPrice third = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(22, third.getPriceWithVAT());
        assertEquals(2, third.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatNotIncludedASNone() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 10, 10);
        //fourth: event price vat not included, additional service VAT n/a
        TotalPrice fourth = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(21, fourth.getPriceWithVAT());
        assertEquals(1, fourth.getVAT());
    }

    @Test
    public void testExtractSummaryVatNotIncluded() {
        initReservationWithTicket(1000, false);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(1100, 100, 0, 0, "CHF"));
        assertEquals(1, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatIncluded() {
        initReservationWithTicket(1000, true);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
        assertEquals(1, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatIncludedExempt() {
        initReservationWithTicket(1000, true);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, PriceContainer.VatStatus.INCLUDED_EXEMPT,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
        assertEquals(1, summaryRows.size());
        assertEquals("9.09", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatNotIncludedExempt() {
        initReservationWithTicket(1000, true);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
        assertEquals(1, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatNotIncludedASInherited() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null,  event, Locale.ENGLISH, null, new TotalPrice(2200, 200, 0, 0, "CHF"));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatIncludedASInherited() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(2000, 182, 0, 0, "CHF"));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatNotIncludedASNone() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatIncludedASNone() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(2000, 100, 0, 0, "CHF"));
        assertEquals(2, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
        assertEquals("10.00", summaryRows.get(1).getPrice());
    }

    private void initReservationWithTicket(int ticketPaidPrice, boolean eventVatIncluded) {
        when(event.isVatIncluded()).thenReturn(eventVatIncluded);
        when(event.getVatStatus()).thenReturn(eventVatIncluded ? PriceContainer.VatStatus.INCLUDED : PriceContainer.VatStatus.NOT_INCLUDED);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
        when(event.getId()).thenReturn(1);
        when(eventRepository.findByReservationId(eq(TICKET_RESERVATION_ID))).thenReturn(event);
        when(ticketReservationRepository.findReservationById(eq(TICKET_RESERVATION_ID))).thenReturn(reservation);
        when(ticket.getSrcPriceCts()).thenReturn(ticketPaidPrice);
        when(ticket.getCategoryId()).thenReturn(1);
        when(ticketRepository.findTicketsInReservation(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        when(ticketCategoryRepository.getByIdAndActive(eq(1), eq(1))).thenReturn(ticketCategory);
        when(reservation.getId()).thenReturn(TICKET_RESERVATION_ID);
    }

    private void initReservationWithAdditionalServices(boolean eventVatIncluded, AdditionalService.VatType additionalServiceVatType, int ticketSrcPrice, int asSrcPrice) {

        initReservationWithTicket(ticketSrcPrice, eventVatIncluded);

        AdditionalServiceItem additionalServiceItem = mock(AdditionalServiceItem.class);
        when(additionalServiceItem.getCurrencyCode()).thenReturn("CHF");
        AdditionalService additionalService = mock(AdditionalService.class);
        when(additionalService.getCurrencyCode()).thenReturn("CHF");

        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(additionalServiceItem));
        when(additionalServiceItem.getAdditionalServiceId()).thenReturn(1);
        when(additionalServiceRepository.getById(eq(1), eq(1))).thenReturn(additionalService);
        when(additionalServiceItem.getSrcPriceCts()).thenReturn(asSrcPrice);
        when(additionalService.getVatType()).thenReturn(additionalServiceVatType);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());
        AdditionalServiceText text = mock(AdditionalServiceText.class);
        when(text.getId()).thenReturn(1);
        when(text.getLocale()).thenReturn("en");
        when(additionalServiceTextRepository.findBestMatchByLocaleAndType(anyInt(), eq("en"), eq(AdditionalServiceText.TextType.TITLE))).thenReturn(text);
    }



}