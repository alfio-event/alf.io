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

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EventUtilTest {

    private static final Predicate<Event> TICKETS_AVAILABLE = (ev) -> false;
    private static final Predicate<Event> TICKETS_NOT_AVAILABLE = (ev) -> true;

    private Event event;
    private SaleableTicketCategory first;
    private SaleableTicketCategory last;
    private ConfigurationManager configurationManager;
    
    private final ZoneId zone = ZoneId.systemDefault();

    @BeforeEach
    void setUp() {
        event = mock(Event.class);
        first = mock(SaleableTicketCategory.class);
        last = mock(SaleableTicketCategory.class);
        configurationManager = mock(ConfigurationManager.class);
        
        when(event.getZoneId()).thenReturn(zone);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getAvailableTickets()).thenReturn(0);
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusHours(2));
    }

    @Test
    @DisplayName("display the waiting queue form if the last category is not expired and sold-out")
    void displayWaitingQueueFormIfSoldOut() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertTrue(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("display the waiting queue form if the last category is not expired and sold-out (reversed)")
    void displayWaitingQueueFormIfSoldOutReversed() {
        List<SaleableTicketCategory> categories = asList(last, first);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertTrue(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("display the waiting queue form if the only category is not expired and sold-out")
    void displayWaitingQueueFormIfSingleCategorySoldOut() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertTrue(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("do not display the waiting queue form if there are yet available seats")
    void doNotDisplayWaitingQueueFormIfAvailableSeats() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(last.getAvailableTickets()).thenReturn(1);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("do not display the waiting queue form if the last category is expired")
    void doNotDisplayWaitingQueueFormIfCategoryExpired() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(last.getAvailableTickets()).thenReturn(0);
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1).minusHours(1));
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("do not display the waiting queue form if the only category is not expired")
    void doNotDisplayWaitingQueueFormIfCategoryNotExpired() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(last.getAvailableTickets()).thenReturn(0);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("do not display the waiting queue form if the category list is empty")
    void doNotDisplayWaitingQueueFormIfNoCategories() {
        List<SaleableTicketCategory> categories = Collections.emptyList();
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("do not display the waiting queue form if the waiting queue is disabled")
    void doNotDisplayWaitingQueueFormIfDisabled() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(false);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("display the waiting queue form before sales start")
    void displayWaitingQueueFormBeforeSalesStart() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getAvailableTickets()).thenReturn(1);
        assertTrue(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("display the waiting queue form before sales start (2 categories)")
    void displayWaitingQueueFormBeforeSalesStart2() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
        when(first.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getAvailableTickets()).thenReturn(1);
        when(first.getAvailableTickets()).thenReturn(1);
        assertTrue(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("not display the waiting queue form before sales start if pre-registration is not enabled")
    void doNotDisplayFormBeforeStartIfPreRegistrationDisabled() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(false);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getAvailableTickets()).thenReturn(1);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("not display the waiting queue form after sales start")
    void doNotDisplayWaitingQueueFormAfterSalesStart() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getAvailableTickets()).thenReturn(1);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("not display the waiting queue form after sales start (two categories)")
    void doNotDisplayWaitingQueueFormAfterSalesStart2() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
        when(first.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getAvailableTickets()).thenReturn(1);
        when(first.getAvailableTickets()).thenReturn(1);
        assertFalse(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE));
    }

    @Test
    @DisplayName("recognize pre-sales period")
    void recognizePreSalesPeriod() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getAvailableTickets()).thenReturn(0);
        assertTrue(EventUtil.isPreSales(event, categories));
    }

    @Test
    @DisplayName("recognize pre-sales from first category")
    void recognizePreSalesFromFirstCategory() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1).plusHours(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2).plusHours(1));
        when(last.getAvailableTickets()).thenReturn(5);
        when(first.getAvailableTickets()).thenReturn(5);
        assertTrue(EventUtil.isPreSales(event, categories));
    }

    @Test
    @DisplayName("recognize post-sales period")
    void recognizePostSalesPeriod() {
        List<SaleableTicketCategory> categories = Collections.singletonList(last);
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(last.getAvailableTickets()).thenReturn(5);
        assertFalse(EventUtil.isPreSales(event, categories));
    }

    @Test
    void recognizePostSalesFromFirstCategory() {
        List<SaleableTicketCategory> categories = asList(first, last);
        when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
        when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
        when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
        when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
        when(last.getAvailableTickets()).thenReturn(5);
        when(first.getAvailableTickets()).thenReturn(5);
        assertFalse(EventUtil.isPreSales(event, categories));
    }

    private final int hundred = 10000;//100.00

    @Test
    @DisplayName("return price if event is not free of charge")
    void evaluatePriceIfEventIsNotFreeOfCharge() {
        assertEquals(hundred, EventUtil.evaluatePrice(hundred, false));
    }

    @Test
    @DisplayName("return price if event is not free of charge")
    void evaluatePriceIfEventIsFreeOfCharge() {
        assertEquals(0, EventUtil.evaluatePrice(hundred, true));
    }


}