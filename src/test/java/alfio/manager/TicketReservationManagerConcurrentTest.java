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
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.model.SpecialPrice;
import alfio.model.TicketReservation;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.SpecialPriceRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static alfio.manager.TicketReservationManagerIntegrationTest.DESCRIPTION;
import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class TicketReservationManagerConcurrentTest {

    private static final String ACCESS_CODE = "MY_ACCESS_CODE";

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private PromoCodeDiscountRepository promoCodeDiscountRepository;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private SpecialPriceTokenGenerator specialPriceTokenGenerator;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;
    @Autowired
    private SpecialPriceRepository specialPriceRepository;

    private Event event;
    private String username;
    private int firstCategoryId;
    private PromoCodeDiscount promoCodeDiscount;
    private TransactionTemplate transactionTemplate;

    @Before
    public void setUp() {
        var transactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate = new TransactionTemplate(platformTransactionManager, transactionDefinition);

        transactionTemplate.execute(tx -> {
            List<TicketCategoryModification> categories = Collections.singletonList(
                new TicketCategoryModification(null, "default", AVAILABLE_SEATS,
                    new DateTimeModification(LocalDate.now(), LocalTime.now()),
                    new DateTimeModification(LocalDate.now(), LocalTime.now()),
                    DESCRIPTION, BigDecimal.TEN, true, "", true, null,
                    null, null, null, null, null));

            Pair<Event, String> eventStringPair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            event = eventStringPair.getLeft();
            username = eventStringPair.getRight();
            int eventId = event.getId();
            firstCategoryId = ticketCategoryRepository.findByEventId(eventId).get(0).getId();

            specialPriceTokenGenerator.generatePendingCodesForCategory(firstCategoryId);
            promoCodeDiscountRepository.addPromoCode(ACCESS_CODE, eventId, event.getOrganizationId(), ZonedDateTime.now(), ZonedDateTime.now().plusDays(1), 0, PromoCodeDiscount.DiscountType.NONE, null, 100, null, null, PromoCodeDiscount.CodeType.ACCESS, firstCategoryId);
            promoCodeDiscount = promoCodeDiscountRepository.findPromoCodeInEventOrOrganization(eventId, ACCESS_CODE).orElseThrow();
            return null;
        });
    }

    @Test
    public void testConcurrentAccessCode() throws InterruptedException {
        var pool = Executors.newFixedThreadPool(AVAILABLE_SEATS);
        var callableList = new ArrayList<Callable<List<SpecialPrice>>>();
        for (int i = 0; i < AVAILABLE_SEATS; i++) {
            callableList.add(() -> {
                return transactionTemplate.execute(tx -> {
                    TicketReservationModification tr = new TicketReservationModification();
                    tr.setAmount(1);
                    tr.setTicketCategoryId(firstCategoryId);
                    TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
                    return ticketReservationManager.reserveTokensForAccessCode(UUID.randomUUID().toString(), mod, promoCodeDiscount);
                });
            });
        }
        long count = pool.invokeAll(callableList)
            .stream()
            .flatMap(f -> {
                try {
                    return f.get().stream().map(SpecialPrice::getCode);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                    return Stream.empty();
                }
            })
            .distinct()
            .count();

        assertEquals(AVAILABLE_SEATS, count);
    }

    @Test
    public void testExpirationDuringReservation() {
        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(1);
        tr.setTicketCategoryId(firstCategoryId);
        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        var id1 = ticketReservationManager.createTicketReservation(event, List.of(mod), List.of(), DateUtils.addHours(new Date(), -1),
            Optional.empty(), Optional.of(ACCESS_CODE), Locale.ENGLISH, false);
        var id2 = ticketReservationManager.createTicketReservation(event, List.of(mod), List.of(), DateUtils.addHours(new Date(), 1),
            Optional.empty(), Optional.of(ACCESS_CODE), Locale.ENGLISH, false);

        ticketReservationManager.cleanupExpiredReservations(new Date());

        assertTrue(ticketReservationManager.findById(id1).isEmpty());
        Optional<TicketReservation> res2 = ticketReservationManager.findById(id2);
        assertTrue(res2.isPresent());
        assertEquals(1, specialPriceRepository.findAllByCategoryId(firstCategoryId).stream().filter(sp -> sp.getAccessCodeId() != null).count());
    }

    @After
    public void tearDown() throws Exception {
        transactionTemplate.execute(tx -> {
            eventManager.deleteEvent(event.getId(), username);
            return null;
        });
    }
}
