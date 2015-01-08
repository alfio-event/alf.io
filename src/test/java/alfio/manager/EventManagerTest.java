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

import alfio.model.TicketCategory;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketRepository;
import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.insightfullogic.lambdabehave.Suite.describe;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(JunitSuiteRunner.class)
public class EventManagerTest {{

    final int hundred = 10000;//100.00
    describe("evaluatePrice", it -> {
        it.should("deduct vat if included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, true, false)).is(9091));
        it.should("not deduct vat if not included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, false)).is(hundred));
        it.should("return BigDecimal.ZERO if the event is free of charge", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, true)).is(0));
    });

    describe("handleTicketNumberModification", it -> {
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);
        EventManager eventManager = new EventManager(null, null, null, ticketRepository, null, null, null, null, jdbc);
        when(original.getId()).thenReturn(20);
        when(updated.getId()).thenReturn(30);
        when(original.getPriceInCents()).thenReturn(1000);
        when(updated.getPriceInCents()).thenReturn(1000);
        when(original.getMaxTickets()).thenReturn(10);
        when(updated.getMaxTickets()).thenReturn(11);
        it.should("throw exception if there are tickets already sold", expect -> {
            when(ticketRepository.selectTicketInCategoryForUpdate(10, 30, 2)).thenReturn(Arrays.asList(1));
            expect.exception(IllegalStateException.class, () -> eventManager.handleTicketNumberModification(10, original, updated, -2));
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
        });
        it.should("invalidate exceeding tickets", expect -> {
            final List<Integer> ids = Arrays.asList(1, 2);
            when(ticketRepository.selectTicketInCategoryForUpdate(10, 30, 2)).thenReturn(ids);
            eventManager.handleTicketNumberModification(10, original, updated, -2);
            verify(ticketRepository, times(1)).invalidateTickets(ids);
        });
        it.should("do nothing if the difference is zero", expect -> {
            eventManager.handleTicketNumberModification(10, original, updated, 0);
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
            verify(jdbc, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
        });

        it.should("insert a new Ticket if the difference is 1", expect -> {
            eventManager.handleTicketNumberModification(10, original, updated, 1);
            verify(ticketRepository, never()).invalidateTickets(anyListOf(Integer.class));
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(1);
        });
    });

    describe("handlePriceChange", it -> {
        TicketRepository ticketRepository = it.usesMock(TicketRepository.class);
        EventManager eventManager = new EventManager(null, null, null, ticketRepository, null, null, null, null, null);
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);

        it.should("do nothing if the price is not changed", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(10);
            eventManager.handlePriceChange(10, original, updated);
            verify(ticketRepository, never()).selectTicketInCategoryForUpdate(anyInt(), anyInt(), anyInt());
            verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt());
        });

        it.should("throw an exception if there aren't enough tickets", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(11);
            when(updated.getMaxTickets()).thenReturn(2);
            when(updated.getId()).thenReturn(20);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(10), eq(20), eq(2))).thenReturn(Arrays.asList(1));
            expect.exception(IllegalStateException.class, () -> eventManager.handlePriceChange(10, original, updated));
            verify(ticketRepository, never()).updateTicketPrice(anyInt(), anyInt(), anyInt());
        });

        it.should("update tickets if constraints are verified", expect -> {
            when(original.getPriceInCents()).thenReturn(10);
            when(updated.getPriceInCents()).thenReturn(11);
            when(updated.getMaxTickets()).thenReturn(2);
            when(updated.getId()).thenReturn(20);
            when(ticketRepository.selectTicketInCategoryForUpdate(eq(10), eq(20), eq(2))).thenReturn(Arrays.asList(1, 2));
            eventManager.handlePriceChange(10, original, updated);
            verify(ticketRepository, times(1)).updateTicketPrice(20, 10, 11);
        });
    });

    describe("handleTokenModification", it -> {
        SpecialPriceRepository specialPriceRepository = it.usesMock(SpecialPriceRepository.class);
        NamedParameterJdbcTemplate jdbc = it.usesMock(NamedParameterJdbcTemplate.class);
        EventManager eventManager = new EventManager(null, null, null, null, null, specialPriceRepository, null, null, jdbc);
        TicketCategory original = Mockito.mock(TicketCategory.class);
        TicketCategory updated = Mockito.mock(TicketCategory.class);

        it.should("do nothing if both categories are not 'access restricted'", expect -> {
            when(original.isAccessRestricted()).thenReturn(false);
            when(updated.isAccessRestricted()).thenReturn(false);
            eventManager.handleTokenModification(original, updated, 50);
            verify(jdbc, never()).batchUpdate(anyString(), any(SqlParameterSource[].class));
        });

        it.should("handle the activation of access restriction", expect -> {
            when(original.isAccessRestricted()).thenReturn(false);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getMaxTickets()).thenReturn(50);
            eventManager.handleTokenModification(original, updated, 50);
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(50);
        });

        it.should("handle the deactivation of access restriction", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(false);
            when(updated.getId()).thenReturn(20);
            eventManager.handleTokenModification(original, updated, 50);
            verify(specialPriceRepository, times(1)).cancelExpiredTokens(eq(20));
        });

        it.should("handle the ticket addition", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            eventManager.handleTokenModification(original, updated, 50);
            ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
            verify(jdbc, times(1)).batchUpdate(anyString(), captor.capture());
            expect.that(captor.getValue().length).is(50);
        });

        it.should("handle the ticket removal", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getId()).thenReturn(20);
            final List<Integer> ids = Arrays.asList(1, 2);
            when(specialPriceRepository.lockTokens(eq(20), eq(2))).thenReturn(ids);
            eventManager.handleTokenModification(original, updated, -2);
            verify(specialPriceRepository, times(1)).cancelTokens(ids);
        });

        it.should("fail if there are not enough tickets", expect -> {
            when(original.isAccessRestricted()).thenReturn(true);
            when(updated.isAccessRestricted()).thenReturn(true);
            when(updated.getId()).thenReturn(20);
            final List<Integer> ids = Arrays.asList(1);
            when(specialPriceRepository.lockTokens(eq(20), eq(2))).thenReturn(ids);
            expect.exception(IllegalArgumentException.class, () -> eventManager.handleTokenModification(original, updated, -2));
            verify(specialPriceRepository, never()).cancelTokens(anyListOf(Integer.class));
        });
    });



}}