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
import alfio.manager.support.reservation.OrderSummaryGenerator;
import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.manager.support.reservation.ReservationEmailContentHelper;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.TestUtil;
import alfio.util.Json;
import alfio.util.TemplateManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketReservationManagerUnitTest {
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
    private PurchaseContextFieldRepository purchaseContextFieldRepository;
    private AdditionalServiceRepository additionalServiceRepository;
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    private AdditionalServiceTextRepository additionalServiceTextRepository;
    private InvoiceSequencesRepository invoiceSequencesRepository;
    private AuditingRepository auditingRepository;
    private UserRepository userRepository;
    private ExtensionManager extensionManager;
    private GroupManager groupManager;
    private Json json;
    private ReservationCostCalculator reservationCostCalculator;

    @BeforeEach
    public void setUp() {
        promoCodeDiscountRepository = mock(PromoCodeDiscountRepository.class);
        ticketRepository = mock(TicketRepository.class);
        ticketReservationRepository = mock(TicketReservationRepository.class);
        eventRepository = mock(EventRepository.class);
        reservation = mock(TicketReservation.class);
        event = mock(Event.class);
        when(event.getCurrency()).thenReturn("CHF");
        when(event.event()).thenReturn(Optional.of(event));
        ticket = mock(Ticket.class);
        when(ticket.getCurrencyCode()).thenReturn("CHF");
        when(ticket.getCategoryId()).thenReturn(1);
        ticketCategory = mock(TicketCategory.class);
        when(ticketCategory.getId()).thenReturn(1);
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
        purchaseContextFieldRepository = mock(PurchaseContextFieldRepository.class);
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
        var additionalServiceManager = new AdditionalServiceManager(additionalServiceRepository, additionalServiceTextRepository, additionalServiceItemRepository, mock(NamedParameterJdbcTemplate.class), mock(TicketRepository.class), purchaseContextFieldRepository);

        when(messageSourceManager.getMessageSourceFor(any())).thenReturn(messageSource);
        when(messageSourceManager.getRootMessageSource()).thenReturn(messageSource);
        var purchaseContextManager = mock(PurchaseContextManager.class);
        when(purchaseContextManager.findByReservationId(anyString())).thenReturn(Optional.of(event));
        reservationCostCalculator = mock(ReservationCostCalculator.class);
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
                purchaseContextFieldRepository,
            additionalServiceManager,
            auditingRepository,
            userRepository,
            extensionManager,
            mock(TicketSearchRepository.class),
            groupManager,
            billingDocumentRepository,
            mock(NamedParameterJdbcTemplate.class),
            json,
            mock(BillingDocumentManager.class),
            TestUtil.clockProvider(),
            purchaseContextManager,
            mock(SubscriptionRepository.class),
            mock(UserManager.class),
            mock(ApplicationEventPublisher.class),
            reservationCostCalculator,
            mock(ReservationEmailContentHelper.class),
            mock(ReservationFinalizer.class),
            mock(OrderSummaryGenerator.class),
            mock(AdditionalServiceItemRepository.class));
    }

    @Test
    void totalReservationCostByID() {
        manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        verify(reservationCostCalculator).totalReservationCostWithVAT(TICKET_RESERVATION_ID);
    }

    @Test
    void totalReservationCost() {
        manager.totalReservationCostWithVAT(reservation);
        verify(reservationCostCalculator).totalReservationCostWithVAT(reservation);
    }

    @Nested
    class GenerateSummary {

        private OrderSummaryGenerator generator;

        @BeforeEach
        void setUp() {
            var subscriptionRepository = mock(SubscriptionRepository.class);
            generator = new OrderSummaryGenerator(ticketReservationRepository, auditingRepository, paymentManager, ticketCategoryRepository, additionalServiceTextRepository, subscriptionRepository, ticketRepository, messageSourceManager,
                new ReservationCostCalculator(ticketReservationRepository, mock(PurchaseContextManager.class), promoCodeDiscountRepository, subscriptionRepository, ticketRepository, additionalServiceRepository, additionalServiceItemRepository));
        }

        @Test
        void testExtractSummaryVatNotIncluded() {
            initReservationWithTicket(1000, false);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(1100, 100, 0, 0, "CHF"));
            Assertions.assertEquals(1, summaryRows.size());
            Assertions.assertEquals("10.00", summaryRows.get(0).getPrice());
        }

        @Test
        void testExtractSummaryVatIncluded() {
            initReservationWithTicket(1000, true);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
            Assertions.assertEquals(1, summaryRows.size());
            Assertions.assertEquals("10.00", summaryRows.get(0).getPrice());
        }

        @Test
        void testExtractSummaryVatIncludedExempt() {
            initReservationWithTicket(1000, true);
            when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED_EXEMPT);
            when(ticket.getFinalPriceCts()).thenReturn(909);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, PriceContainer.VatStatus.INCLUDED_EXEMPT,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
            Assertions.assertEquals(1, summaryRows.size());
            Assertions.assertEquals("9.09", summaryRows.get(0).getPrice());
        }

        @Test
        void testExtractSummaryVatNotIncludedExempt() {
            initReservationWithTicket(1000, false);
            when(ticket.getVatStatus()).thenReturn(PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT);
            when(ticket.getFinalPriceCts()).thenReturn(1000);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, PriceContainer.VatStatus.NOT_INCLUDED_EXEMPT,  event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
            Assertions.assertEquals(1, summaryRows.size());
            Assertions.assertEquals("10.00", summaryRows.get(0).getPrice());
        }

        @Test
        void testExtractSummaryVatNotIncludedASInherited() {
            initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 1000, 1000);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null,  event, Locale.ENGLISH, null, new TotalPrice(2200, 200, 0, 0, "CHF"));
            Assertions.assertEquals(2, summaryRows.size());
            summaryRows.forEach(r -> Assertions.assertEquals("10.00", r.getPrice(), String.format("%s failed", r.getType())));
        }

        @Test
        void testExtractSummaryVatIncludedASInherited() {
            initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 1000, 1000);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(2000, 182, 0, 0, "CHF"));
            Assertions.assertEquals(2, summaryRows.size());
            summaryRows.forEach(r -> Assertions.assertEquals("10.00", r.getPrice(), String.format("%s failed", r.getType())));
        }

        @Test
        void testExtractSummaryVatNotIncludedASNone() {
            initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 1000, 1000);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(1000, 100, 0, 0, "CHF"));
            Assertions.assertEquals(2, summaryRows.size());
            summaryRows.forEach(r -> Assertions.assertEquals("10.00", r.getPrice(), String.format("%s failed", r.getType())));
        }

        @Test
        void testExtractSummaryVatIncludedASNone() {
            initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 1000, 1000);
            List<SummaryRow> summaryRows = generator.extractSummary(TICKET_RESERVATION_ID, null, event, Locale.ENGLISH, null, new TotalPrice(2000, 100, 0, 0, "CHF"));
            Assertions.assertEquals(2, summaryRows.size());
            Assertions.assertEquals("10.00", summaryRows.get(0).getPrice());
            Assertions.assertEquals("10.00", summaryRows.get(1).getPrice());
        }
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
        when(ticketCategoryRepository.getByIdsAndActive(anyCollection(), eq(1))).thenReturn(List.of(ticketCategory));
        when(reservation.getId()).thenReturn(TICKET_RESERVATION_ID);
    }

    private void initReservationWithAdditionalServices(boolean eventVatIncluded, AdditionalService.VatType additionalServiceVatType, int ticketSrcPrice, int asSrcPrice) {

        initReservationWithTicket(ticketSrcPrice, eventVatIncluded);

        AdditionalServiceItem additionalServiceItem = mock(AdditionalServiceItem.class);
        when(additionalServiceItem.getCurrencyCode()).thenReturn("CHF");
        AdditionalService additionalService = mock(AdditionalService.class);
        when(additionalService.getCurrencyCode()).thenReturn("CHF");
        when(additionalService.getId()).thenReturn(1);

        when(additionalServiceItemRepository.findByReservationUuid(anyInt(), eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(additionalServiceItem));
        when(additionalServiceItem.getAdditionalServiceId()).thenReturn(1);
        when(additionalServiceRepository.loadAllForEvent(eq(1))).thenReturn(List.of(additionalService));
        when(additionalServiceRepository.getById(eq(1), eq(1))).thenReturn(additionalService);
        when(additionalServiceItem.getSrcPriceCts()).thenReturn(asSrcPrice);
        when(additionalService.getVatType()).thenReturn(additionalServiceVatType);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(anyInt(), eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());
        AdditionalServiceText text = mock(AdditionalServiceText.class);
        when(text.getId()).thenReturn(1);
        when(text.getLocale()).thenReturn("en");
        when(additionalServiceTextRepository.findBestMatchByLocaleAndType(anyInt(), eq("en"), eq(AdditionalServiceText.TextType.TITLE))).thenReturn(text);
    }

    @Test
    void doNotCallExtensionIfTicketsAreFreeOfCharge() {
        var ticketReservationMock = mock(TicketReservationWithOptionalCodeModification.class);
        when(ticketReservationMock.getTicketCategoryId()).thenReturn(1);
        when(ticketCategoryRepository.countPaidCategoriesInReservation(anyCollection())).thenReturn(0);
        Optional<String> result = manager.createDynamicPromoCodeIfNeeded(event, List.of(ticketReservationMock), TICKET_RESERVATION_ID);
        Assertions.assertTrue(result.isEmpty());
        verifyNoInteractions(extensionManager);
    }

    @Test
    void callExtensionIfTicketsMustBePaid() {
        var ticketReservationMock = mock(TicketReservationWithOptionalCodeModification.class);
        when(ticketReservationMock.getTicketCategoryId()).thenReturn(1);
        when(ticketCategoryRepository.countPaidCategoriesInReservation(anyCollection())).thenReturn(1);
        var promoCode = mock(PromoCodeDiscount.class);
        when(promoCode.getPromoCode()).thenReturn("abcd");
        when(extensionManager.handleDynamicDiscount(eq(event), anyMap(), eq(TICKET_RESERVATION_ID))).thenReturn(Optional.of(promoCode));
        when(promoCodeDiscountRepository.addPromoCodeIfNotExists(eq("abcd"), any(), anyInt(), any(), any(), anyInt(), any(), any(), any(), isNull(), isNull(), any(), isNull())).thenReturn(0);
        Optional<String> result = manager.createDynamicPromoCodeIfNeeded(event, List.of(ticketReservationMock), TICKET_RESERVATION_ID);
        Assertions.assertFalse(result.isEmpty());
        verifyNoInteractions(auditingRepository);
    }

    @Test
    void addAuditingIfPromoCodeHasBeenCreated() {
        var ticketReservationMock = mock(TicketReservationWithOptionalCodeModification.class);
        when(ticketReservationMock.getTicketCategoryId()).thenReturn(1);
        when(ticketCategoryRepository.countPaidCategoriesInReservation(anyCollection())).thenReturn(1);
        var promoCode = mock(PromoCodeDiscount.class);
        when(promoCode.getPromoCode()).thenReturn("abcd");
        when(extensionManager.handleDynamicDiscount(eq(event), anyMap(), eq(TICKET_RESERVATION_ID))).thenReturn(Optional.of(promoCode));
        when(promoCodeDiscountRepository.addPromoCodeIfNotExists(eq("abcd"), any(), anyInt(), any(), any(), anyInt(), any(), any(), any(), isNull(), isNull(), any(), isNull())).thenReturn(1);
        Optional<String> result = manager.createDynamicPromoCodeIfNeeded(event, List.of(ticketReservationMock), TICKET_RESERVATION_ID);
        Assertions.assertFalse(result.isEmpty());
        verify(auditingRepository).insert(eq(TICKET_RESERVATION_ID), any(), anyInt(), eq(Audit.EventType.DYNAMIC_DISCOUNT_CODE_CREATED), any(), eq(Audit.EntityType.RESERVATION), eq(TICKET_RESERVATION_ID));
    }
}