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
        it.should("not display the sold-out warning if the waiting list is disabled", expect -> {
            List<SaleableTicketCategory> categories = Collections.singletonList(last);
            when(configurationManager.getBooleanConfigValue(eq(Configuration.event(event)), eq(ConfigurationKeys.ENABLE_WAITING_QUEUE), eq(false))).thenReturn(false);
            expect.that(EventUtil.displaySoldOutWarning(event, categories, configurationManager)).is(false);
        });

        it.completesWith(() -> verifyNoMoreInteractions(first));
    });
}}