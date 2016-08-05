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
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static alfio.model.system.ConfigurationKeys.ENABLE_PRE_REGISTRATION;
import static alfio.model.system.ConfigurationKeys.ENABLE_WAITING_QUEUE;
import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class EventUtilTest {

    public static final Predicate<Event> TICKETS_AVAILABLE = (ev) -> false;
    public static final Predicate<Event> TICKETS_NOT_AVAILABLE = (ev) -> true;

    static {
    describe("EventUtil", it -> {
        Event event = mock(Event.class);
        ZoneId zone = ZoneId.systemDefault();
        when(event.getZoneId()).thenReturn(zone);
        SaleableTicketCategory first = it.usesMock(SaleableTicketCategory.class);
        SaleableTicketCategory last = it.usesMock(SaleableTicketCategory.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);

        //sold-out

        it.should("display the waiting queue form if the last category is not expired and sold-out", expect -> {
            List<SaleableTicketCategory> categories = asList(first, last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusHours(2));

            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(true);
        });

        it.should("display the waiting queue form if the last category is not expired and sold-out (reversed)", expect -> {
            List<SaleableTicketCategory> categories = asList(last, first);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusHours(2));
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(true);
        });
        it.should("display the waiting queue form if the only category is not expired and sold-out", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(true);
        });
        it.should("not display the waiting queue form if there are yet available seats", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(1);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(false);
        });
        it.should("not display the waiting queue form if the last category is expired", expect -> {
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
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(false);
        });
        it.should("not display the waiting queue form if the only category is not expired", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(false);
        });
        it.should("not display the waiting queue form if the category list is empty", expect -> {
            List<SaleableTicketCategory> categories = Collections.emptyList();
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(true);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(false);
        });
        it.should("not display the waiting queue form if the waiting queue is disabled", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(2));
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_WAITING_QUEUE)), eq(false))).thenReturn(false);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_NOT_AVAILABLE)).is(false);
        });

        //pre-sales registration

        it.should("display the waiting queue form before sales start", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(1);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(true);
        });

        it.should("display the waiting queue form before sales start (2 categories)", expect -> {
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
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(true);
        });

        it.should("not display the waiting queue form before sales start if pre-registration is not enabled", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(false);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(1);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(false);
        });

        it.should("not display the waiting queue form after sales start", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.from(event.getOrganizationId(), event.getId(), ENABLE_PRE_REGISTRATION)), eq(false))).thenReturn(true);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(1);
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(false);
        });

        it.should("not display the waiting queue form after sales start (two categories)", expect -> {
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
            expect.that(EventUtil.displayWaitingQueueForm(event, categories, configurationManager, TICKETS_AVAILABLE)).is(false);
        });

        //pre-sales message

        it.should("recognize pre-sales period", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            expect.that(EventUtil.isPreSales(event, categories)).is(true);
        });

        it.should("recognize pre-sales from first category", expect -> {
            List<SaleableTicketCategory> categories = asList(first, last);
            when(first.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(first.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1).plusHours(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(2).plusHours(1));
            when(last.getAvailableTickets()).thenReturn(5);
            when(first.getAvailableTickets()).thenReturn(5);
            expect.that(EventUtil.isPreSales(event, categories)).is(true);
        });


        it.should("recognize post-sales period", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(5);
            expect.that(EventUtil.isPreSales(event, categories)).is(false);
        });

        it.should("recognize post-sales from first category", expect -> {
            List<SaleableTicketCategory> categories = asList(first, last);
            when(first.getZonedInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(first.getUtcInception()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(first.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getZonedInception()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcInception()).thenReturn(ZonedDateTime.now().plusDays(2));
            when(last.getUtcExpiration()).thenReturn(ZonedDateTime.now().plusDays(3));
            when(last.getAvailableTickets()).thenReturn(5);
            when(first.getAvailableTickets()).thenReturn(5);
            expect.that(EventUtil.isPreSales(event, categories)).is(false);
        });

        it.completesWith(() -> verifyNoMoreInteractions(first));
    });

    final int hundred = 10000;//100.00
    describe("evaluatePrice", it -> {
        it.should("return price if event is not free of charge", expect -> expect.that(EventUtil.evaluatePrice(hundred, false)).is(hundred));
        it.should("return BigDecimal.ZERO if the event is free of charge", expect -> expect.that(EventUtil.evaluatePrice(hundred, true)).is(0));
    });
}}