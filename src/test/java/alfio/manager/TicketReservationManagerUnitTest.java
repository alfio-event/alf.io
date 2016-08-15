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

import alfio.manager.plugin.PluginManager;
import alfio.manager.support.SummaryRow;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.repository.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.TemplateManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TicketReservationManagerUnitTest {
    private static final String TICKET_RESERVATION_ID = "abcdef";

    private TicketReservationManager manager;
    @Mock
    private PromoCodeDiscountRepository promoCodeDiscountRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketReservationRepository ticketReservationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TicketReservation reservation;
    @Mock
    private Event event;
    @Mock
    private Ticket ticket;
    @Mock
    private TicketCategory ticketCategory;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private TicketCategoryRepository ticketCategoryRepository;
    @Mock
    private TicketCategoryDescriptionRepository ticketCategoryDescriptionRepository;
    @Mock
    private ConfigurationManager configurationManager;
    @Mock
    private PaymentManager paymentManager;
    @Mock
    private SpecialPriceRepository specialPriceRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private NotificationManager notificationManager;
    @Mock
    private MessageSource messageSource;
    @Mock
    private TemplateManager templateManager;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private WaitingQueueManager waitingQueueManager;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private FileUploadManager fileUploadManager;
    @Mock
    private TicketFieldRepository ticketFieldRepository;
    @Mock
    private AdditionalServiceRepository additionalServiceRepository;
    @Mock
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    @Mock
    private AdditionalServiceTextRepository additionalServiceTextRepository;

    @Before
    public void setUp() {
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
            messageSource,
            templateManager,
            transactionManager,
            waitingQueueManager,
            pluginManager,
            fileUploadManager,
            ticketFieldRepository,
            additionalServiceRepository,
            additionalServiceItemRepository,
            additionalServiceTextRepository);
    }

    @Test
    public void calcReservationCostOnlyTickets() throws Exception {
        when(event.isVatIncluded()).thenReturn(true, false);
        when(event.getVatStatus()).thenReturn(PriceContainer.VatStatus.INCLUDED, PriceContainer.VatStatus.NOT_INCLUDED);
        when(event.getVat()).thenReturn(BigDecimal.TEN);
        when(eventRepository.findByReservationId(eq(TICKET_RESERVATION_ID))).thenReturn(event);
        when(ticketReservationRepository.findReservationById(eq(TICKET_RESERVATION_ID))).thenReturn(reservation);
        when(ticket.getSrcPriceCts()).thenReturn(10);
        when(ticketRepository.findTicketsInReservation(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(ticket));
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());

        TicketReservationManager.TotalPrice included = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(10, included.getPriceWithVAT());
        assertEquals(1, included.getVAT());

        TicketReservationManager.TotalPrice notIncluded = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(11, notIncluded.getPriceWithVAT());
        assertEquals(1, notIncluded.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatIncludedInherited() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 10, 10);
        //first: event price vat included, additional service VAT inherited
        TicketReservationManager.TotalPrice first = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(20, first.getPriceWithVAT());
        assertEquals(2, first.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatIncludedASNoVat() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 10, 10);
        //second: event price vat included, additional service VAT n/a
        TicketReservationManager.TotalPrice second = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(20, second.getPriceWithVAT());
        assertEquals(1, second.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatNotIncludedASInherited() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 10, 10);
        //third: event price vat not included, additional service VAT inherited
        TicketReservationManager.TotalPrice third = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(22, third.getPriceWithVAT());
        assertEquals(2, third.getVAT());
    }

    @Test
    public void calcReservationCostWithASVatNotIncludedASNone() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 10, 10);
        //fourth: event price vat not included, additional service VAT n/a
        TicketReservationManager.TotalPrice fourth = manager.totalReservationCostWithVAT(TICKET_RESERVATION_ID);
        assertEquals(21, fourth.getPriceWithVAT());
        assertEquals(1, fourth.getVAT());
    }

    @Test
    public void testExtractSummaryVatNotIncluded() {
        initReservationWithTicket(1000, false);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(1100, 100, 0, 0));
        assertEquals(1, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatIncluded() {
        initReservationWithTicket(1000, true);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(1000, 100, 0, 0));
        assertEquals(1, summaryRows.size());
        assertEquals("10.00", summaryRows.get(0).getPrice());
    }

    @Test
    public void testExtractSummaryVatNotIncludedASInherited() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.INHERITED, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(2200, 200, 0, 0));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatIncludedASInherited() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.INHERITED, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(2000, 182, 0, 0));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatNotIncludedASNone() {
        initReservationWithAdditionalServices(false, AdditionalService.VatType.NONE, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(1000, 100, 0, 0));
        assertEquals(2, summaryRows.size());
        summaryRows.forEach(r -> assertEquals(String.format("%s failed", r.getType()), "10.00", r.getPrice()));
    }

    @Test
    public void testExtractSummaryVatIncludedASNone() {
        initReservationWithAdditionalServices(true, AdditionalService.VatType.NONE, 1000, 1000);
        List<SummaryRow> summaryRows = manager.extractSummary(TICKET_RESERVATION_ID, event, Locale.ENGLISH, null, new TicketReservationManager.TotalPrice(2000, 100, 0, 0));
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
        when(ticketCategoryRepository.getById(eq(1), eq(1))).thenReturn(ticketCategory);
    }

    private void initReservationWithAdditionalServices(boolean eventVatIncluded, AdditionalService.VatType additionalServiceVatType, int ticketSrcPrice, int asSrcPrice) {

        initReservationWithTicket(ticketSrcPrice, eventVatIncluded);

        AdditionalServiceItem additionalServiceItem = mock(AdditionalServiceItem.class);
        AdditionalService additionalService = mock(AdditionalService.class);

        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.singletonList(additionalServiceItem));
        when(additionalServiceItem.getAdditionalServiceId()).thenReturn(1);
        when(additionalServiceRepository.getById(eq(1), eq(1))).thenReturn(additionalService);
        when(additionalServiceItem.getSrcPriceCts()).thenReturn(asSrcPrice);
        when(additionalService.getVatType()).thenReturn(additionalServiceVatType);
        AdditionalServiceItemRepository additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        when(additionalServiceItemRepository.findByReservationUuid(eq(TICKET_RESERVATION_ID))).thenReturn(Collections.emptyList());
        AdditionalServiceText text = mock(AdditionalServiceText.class);
        when(additionalServiceTextRepository.findByLocaleAndType(anyInt(), eq("en"), eq(AdditionalServiceText.TextType.TITLE))).thenReturn(text);
    }



}