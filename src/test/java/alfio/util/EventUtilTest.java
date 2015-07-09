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
import alfio.model.system.ConfigurationKeys;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class EventUtilTest {{
    describe("displaySoldOutWarning", it -> {
        Event event = mock(Event.class);
        ZoneId zone = ZoneId.systemDefault();
        when(event.getZoneId()).thenReturn(zone);
        SaleableTicketCategory first = it.usesMock(SaleableTicketCategory.class);
        SaleableTicketCategory last = it.usesMock(SaleableTicketCategory.class);
        ConfigurationManager configurationManager = it.usesMock(ConfigurationManager.class);
        it.should("display the sold-out warning if the last category is not expired and sold-out", expect -> {
            List<SaleableTicketCategory> categories = asList(first, last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(true);
        });
        it.should("display the sold-out warning if the only category is not expired and sold-out", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(true);
        });
        it.should("not display the sold-out warning if there are yet available seats", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().plusDays(1));
            when(last.getAvailableTickets()).thenReturn(1);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });
        it.should("not display the sold-out warning if the last category is expired", expect -> {
            List<SaleableTicketCategory> categories = asList(first, last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });
        it.should("not display the sold-out warning if the only category is not expired", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });
        it.should("not display the sold-out warning if the category list is empty", expect -> {
            List<SaleableTicketCategory> categories = Collections.emptyList();
            when(last.getZonedExpiration()).thenReturn(ZonedDateTime.now().minusDays(1));
            when(last.getAvailableTickets()).thenReturn(0);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(true);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });
        it.should("not display the sold-out warning if the waiting queue is disabled", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(false);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });

        it.completesWith(() -> verifyNoMoreInteractions(first));
    });
}}