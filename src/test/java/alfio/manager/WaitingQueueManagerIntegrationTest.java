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
import alfio.config.WebSecurityConfig;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.result.Result;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.MonetaryUtil;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.model.modification.DateTimeModification.fromZonedDateTime;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, WebSecurityConfig.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class WaitingQueueManagerIntegrationTest extends BaseIntegrationTest {

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private WaitingQueueManager waitingQueueManager;
    @Autowired
    private ConfigurationManager configurationManager;
    @Autowired
    private TicketReservationManager ticketReservationManager;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private WaitingQueueSubscriptionProcessor waitingQueueSubscriptionProcessor;
    @Autowired
    private ClockProvider clockProvider;

    @BeforeEach
    public void init() {
        ensureMinimalConfiguration(configurationRepository);
    }

    private static CustomerName customerJohnDoe(Event event) {
        return new CustomerName("John Doe", "John", "Doe", event.mustUseFirstAndLastName());
    }

    @Test
    public void testSubscribeDenied() {
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(false, AVAILABLE_SEATS, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(), ConfigurationKeys.STOP_WAITING_QUEUE_SUBSCRIPTIONS.name(), "true", "");
        //subscription should now be denied
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
        assertFalse(result);
    }

    @Test
    public void testDistributeSeatsFirstCategoryIsUnbounded() {
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(false, AVAILABLE_SEATS, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
        assertTrue(result);
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(firstCategory.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));

    }

    @Test
    public void testDistributeSeatsFirstCategoryIsBounded() {
        List<TicketCategoryModification> categories = getPreSalesTicketCategoryModifications(true, 10, true, 10);
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getKey();
        TicketCategory firstCategory = eventManager.loadTicketCategories(event).stream().filter(t->t.getName().equals("defaultFirst")).findFirst().orElseThrow(IllegalStateException::new);
        configurationManager.saveCategoryConfiguration(firstCategory.getId(), event.getId(), Collections.singletonList(new ConfigurationModification(null, ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION.getValue(), "1")), pair.getRight()+"_owner");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PRE_REGISTRATION, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        boolean result = waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH);
        assertTrue(result);
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(firstCategory.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));

    }

    @Test
    public void testWaitingQueueForUnboundedCategory() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();
        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.<String>empty(), Locale.ENGLISH, false, null);
        var reservation = ticketReservationManager.findById(reservationId).orElseThrow();
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        var orderSummary = ticketReservationManager.orderSummaryForReservation(reservation, event);
        PaymentSpecification spec = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(), event, "blabla", new CustomerName("a", "b", "c", true), "", null, Locale.ENGLISH, false, false, orderSummary, null, null, PriceContainer.VatStatus.INCLUDED, true, true);
        PaymentResult result = ticketReservationManager.performPayment(spec, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());
        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());
    }

    @Test
    public void testAssignTicketToWaitingQueueUnboundedCategory() {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).minusMinutes(1);
        LocalDateTime end = LocalDateTime.now(clockProvider.getClock()).plusMinutes(20);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory unbounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(unbounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(unbounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event, Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscountSingle = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCostSingle = priceAndDiscountSingle.getLeft();
        assertTrue(priceAndDiscountSingle.getRight().isEmpty());
        specification = new PaymentSpecification(reservationIdSingle, null, reservationCostSingle.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult resultSingle = ticketReservationManager.performPayment(specification, reservationCostSingle, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(resultSingle.isSuccessful());


        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingle, false, false, null);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(unbounded.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));
    }

    @Test
    public void testAssignTicketToWaitingQueueBoundedCategory() {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).minusMinutes(2);
        LocalDateTime end = LocalDateTime.now(clockProvider.getClock()).plusMinutes(20);
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        TicketCategory bounded = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);

        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(AVAILABLE_SEATS - 1);
        tr.setTicketCategoryId(bounded.getId());

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(bounded.getId());

        TicketReservationWithOptionalCodeModification multi = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        TicketReservationWithOptionalCodeModification single = new TicketReservationWithOptionalCodeModification(tr2, Optional.empty());

        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(multi), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());

        String reservationIdSingle = ticketReservationManager.createTicketReservation(event, Collections.singletonList(single), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscountSingle = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCostSingle = priceAndDiscountSingle.getLeft();
        assertTrue(priceAndDiscountSingle.getRight().isEmpty());
        specification = new PaymentSpecification(reservationIdSingle, null, reservationCostSingle.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult resultSingle = ticketReservationManager.performPayment(specification, reservationCostSingle, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(resultSingle.isSuccessful());
        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingle, false, false, null);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(bounded.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));
    }

    @Test
    public void testAssignTicketToWaitingQueueUnboundedCategorySelected() {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).minusHours(1);
        LocalDateTime end = LocalDateTime.now(clockProvider.getClock()).plusHours(1);

        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default2", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");

        Event event = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository).getKey();

        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        TicketCategory first = ticketCategories.get(0);
        TicketCategory second = ticketCategories.get(1);

        TicketReservationModification tr2 = new TicketReservationModification();
        tr2.setAmount(1);
        tr2.setTicketCategoryId(second.getId());

        TicketReservationModification tr3 = new TicketReservationModification();
        tr3.setAmount(1);
        tr3.setTicketCategoryId(first.getId());

        reserveTickets(event, first, AVAILABLE_SEATS - 2);

        String reservationIdSingleFirst = reserveTickets(event, second, 1);
        String reservationIdSingleSecond = reserveTickets(event, second, 1);

        assertEquals(0, eventRepository.findStatisticsFor(event.getId()).getDynamicAllocation());

        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", first.getId(), Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event, new CustomerName("John Doe 2", "John", "Doe 2", event.mustUseFirstAndLastName()), "john@doe2.com", second.getId(), Locale.ENGLISH));

        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingleFirst, false, false, null);
        ticketReservationManager.deleteOfflinePayment(event, reservationIdSingleSecond, false, false, null);

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(2, subscriptions.size());
        Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime> subscriptionDetail = subscriptions.get(0);
        assertEquals("john@doe.com", subscriptionDetail.getLeft().getEmailAddress());
        TicketReservationWithOptionalCodeModification reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(first.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));

        subscriptionDetail = subscriptions.get(1);
        assertEquals("john@doe2.com", subscriptionDetail.getLeft().getEmailAddress());
        reservation = subscriptionDetail.getMiddle();
        assertEquals(Integer.valueOf(second.getId()), reservation.getTicketCategoryId());
        assertEquals(Integer.valueOf(1), reservation.getAmount());
        assertTrue(subscriptionDetail.getRight().isAfter(ZonedDateTime.now(clockProvider.getClock())));
    }

    private String reserveTickets(Event event, TicketCategory category, int num) {
        TicketReservationModification tr = new TicketReservationModification();
        tr.setAmount(num);
        tr.setTicketCategoryId(category.getId());
        TicketReservationWithOptionalCodeModification tcm = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(tcm), Collections.emptyList(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());
        return reservationId;
    }

    @Test
    public void testNoPublicCategoryAvailable() {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).minusHours(1);
        LocalDateTime end = LocalDateTime.now(clockProvider.getClock()).plusHours(1);

        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default2", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        TicketCategory first = ticketCategories.stream().filter(tc -> !tc.isAccessRestricted()).findFirst().orElseThrow(IllegalStateException::new);
        reserveTickets(event, first, 2);
        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event, new CustomerName("John Doe 2", "John", "Doe 2", event.mustUseFirstAndLastName()), "john@doe2.com", null, Locale.ENGLISH));

        ZoneId zoneId = event.getZoneId();
        Result<TicketCategory> ticketCategoryResult = eventManager.updateCategory(first.getId(), event, new TicketCategoryModification(first.getId(), first.getName(), TicketCategory.TicketAccessType.INHERIT, 3,
            fromZonedDateTime(first.getInception(zoneId)), fromZonedDateTime(first.getExpiration(zoneId)), Collections.emptyMap(),
            first.getPrice(), false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()), eventAndUsername.getValue());
        assertTrue(ticketCategoryResult.isSuccess());
        assertEquals(1, ticketRepository.countReleasedTicketInCategory(event.getId(), first.getId()).intValue());
        //now we should have an extra available ticket
        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(1, subscriptions.size());
    }

    @Test
    public void testTicketBelongsToExpiredCategory() {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).minusHours(1);
        LocalDateTime end = LocalDateTime.now(clockProvider.getClock()).plusHours(1);

        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, false, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "default2", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(end.toLocalDate(), end.toLocalTime()),
                DESCRIPTION, BigDecimal.TEN, true, "", true, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));

        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WAITING_QUEUE, "true");
        Pair<Event, String> eventAndUsername = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = eventAndUsername.getKey();
        List<TicketCategory> ticketCategories = ticketCategoryRepository.findAllTicketCategories(event.getId());
        TicketCategory first = ticketCategories.stream().filter(tc -> !tc.isAccessRestricted()).findFirst().orElseThrow(IllegalStateException::new);
        String reservationId = reserveTickets(event, first, 2);
        assertTrue(waitingQueueManager.subscribe(event, customerJohnDoe(event), "john@doe.com", null, Locale.ENGLISH));
        assertTrue(waitingQueueManager.subscribe(event, new CustomerName("John Doe 2", "John", "Doe 2", event.mustUseFirstAndLastName()), "john@doe2.com", null, Locale.ENGLISH));
        ticketCategoryRepository.update(first.getId(), first.getName(), first.getInception(event.getZoneId()), event.now(clockProvider).minusMinutes(1L), first.getMaxTickets(), first.isAccessRestricted(),
            MonetaryUtil.unitToCents(first.getPrice(), first.getCurrencyCode()), first.getCode(), null, null, null, null, first.getTicketCheckInStrategy(), first.getTicketAccessType());

        List<Integer> ticketIds = ticketRepository.findTicketsInReservation(reservationId).stream().map(Ticket::getId).collect(Collectors.toList());
        assertEquals(2, ticketIds.size());

        ticketReservationManager.deleteOfflinePayment(event, reservationId, false, false, null);

        List<TicketInfo> releasedButExpired = ticketRepository.findReleasedBelongingToExpiredCategories(event.getId(), event.now(clockProvider));
        assertEquals(2, releasedButExpired.size());

        List<Triple<WaitingQueueSubscription, TicketReservationWithOptionalCodeModification, ZonedDateTime>> subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(2, subscriptions.size());

        waitingQueueSubscriptionProcessor.revertTicketToFreeIfCategoryIsExpired(event);

        subscriptions = waitingQueueManager.distributeSeats(event).collect(Collectors.toList());
        assertEquals(0, subscriptions.size());

    }

    private List<TicketCategoryModification> getPreSalesTicketCategoryModifications(boolean firstBounded, int firstSeats, boolean lastBounded, int lastSeats) {
        LocalDateTime start = LocalDateTime.now(clockProvider.getClock()).plusMinutes(4);
        return Arrays.asList(
            new TicketCategoryModification(null, "defaultFirst", TicketCategory.TicketAccessType.INHERIT, firstSeats,
                new DateTimeModification(start.toLocalDate(), start.toLocalTime()),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", firstBounded, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "defaultLast", TicketCategory.TicketAccessType.INHERIT, lastSeats,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(2), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", lastBounded, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
    }
}