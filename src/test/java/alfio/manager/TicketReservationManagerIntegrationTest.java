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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.user.UserManager;
import alfio.model.SpecialPrice;
import alfio.model.Ticket;
import alfio.model.modification.*;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles(Initializer.PROFILE_DEV)
public class TicketReservationManagerIntegrationTest {

    @BeforeClass
    public static void initEnv() {
        initSystemProperties();
    }

    @Autowired
    private EventManager eventManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;

    @Test
    public void testPriceIsOverridden() {
        List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false));
        EventWithStatistics event = eventManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(2);
        TicketCategoryWithStatistic category = event.getTicketCategories().get(0);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.<SpecialPrice>empty());
        ticketReservationManager.createTicketReservation(event.getId(), Collections.singletonList(mod), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Collections.singletonList(category.getId()));
        assertEquals(2, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(new BigDecimal("9.90"), t.getOriginalPrice()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(18, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }

    @Test
    public void testTicketSelection() {
        List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", false),
                new TicketCategoryModification(null, "default", 10,
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        new DateTimeModification(LocalDate.now(), LocalTime.now()),
                        "desc", BigDecimal.TEN, false, "", true));
        EventWithStatistics event = eventManager.fillWithStatistics(initEvent(categories, organizationRepository, userManager, eventManager).getKey());

        TicketCategoryWithStatistic bounded = event.getTicketCategories().stream().filter(TicketCategoryWithStatistic::isBounded).findFirst().orElseThrow(IllegalStateException::new);
        TicketCategoryWithStatistic unbounded = event.getTicketCategories().stream().filter(t -> !t.isBounded()).findFirst().orElseThrow(IllegalStateException::new);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(10);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(9);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.<SpecialPrice>empty());
        TicketReservationWithOptionalCodeModification mod2 = new TicketReservationWithOptionalCodeModification(tr2, Optional.<SpecialPrice>empty());
        ticketReservationManager.createTicketReservation(event.getId(), Arrays.asList(mod, mod2), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Optional.<String>empty(), Locale.ENGLISH, false);
        List<Ticket> pendingTickets = ticketRepository.findPendingTicketsInCategories(Arrays.asList(bounded.getId(), unbounded.getId()));
        assertEquals(19, pendingTickets.size());
        pendingTickets.forEach(t -> assertEquals(new BigDecimal("9.90"), t.getOriginalPrice()));
        List<Ticket> tickets = ticketRepository.findFreeByEventId(event.getId());
        assertEquals(1, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getCategoryId() == null));
    }
}