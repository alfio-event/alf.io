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
import alfio.controller.form.ReservationForm;
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.StaticPaymentMethods;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class DiscountIntegrationTest extends BaseIntegrationTest {


    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EventManager eventManager;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private TicketReservationManager ticketReservationManager;

    @Autowired
    private ClockProvider clockProvider;

    @Autowired
    private PromoCodeDiscountRepository promoCodeDiscountRepository;

    @Test
    void checkConcurrency() throws InterruptedException {

        int concurrencyCount = 4;

        var startSignal = new CountDownLatch(concurrencyCount);
        var countdownLatchBeforePerformPayment = new CountDownLatch(concurrencyCount);
        var countdownLatchBeforeComplete = new CountDownLatch(concurrencyCount);
        var doneSignal = new CountDownLatch(concurrencyCount);


        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false,
                "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        var event = eventAndUser.getLeft();

        TicketCategory category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);

        String promoCode = "100_PROMO";

        eventManager.addPromoCode(promoCode,
            event.getId(), null,
            ZonedDateTime.now(clockProvider.getClock()).minusDays(2),
            event.getEnd().plusDays(2),
            50, PromoCodeDiscount.DiscountType.PERCENTAGE, List.of(category.getId()),
            1, "100% discount",
            "test@test.ch",
            PromoCodeDiscount.CodeType.DISCOUNT,
            null,
            null);

        var promoCodeDiscount = promoCodeDiscountRepository.findAllInEvent(event.getId()).get(0);


        for (int i = 0; i < concurrencyCount; i++) {
            Runnable runnable = () -> {
                try {
                    var form = new ReservationForm();
                    var ticketReservation = new TicketReservationModification();
                    ticketReservation.setQuantity(2);
                    ticketReservation.setTicketCategoryId(category.getId());
                    form.setReservation(Collections.singletonList(ticketReservation));
                    form.setPromoCode(promoCode);

                    TicketReservationModification tr = new TicketReservationModification();
                    tr.setQuantity(1);
                    tr.setTicketCategoryId(category.getId());
                    TicketReservationWithOptionalCodeModification mod = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
                    startSignal.countDown();
                    startSignal.await();

                    String reservationId = null;
                    try {
                        reservationId = ticketReservationManager.createTicketReservation(event, Collections.singletonList(mod), Collections.emptyList(),
                            DateUtils.addDays(new Date(), 1),
                            Optional.of(promoCode),
                            Locale.ENGLISH,
                            false,
                            null);


                    } catch (Throwable t) {

                    } finally {
                        countdownLatchBeforePerformPayment.countDown();
                    }
                    countdownLatchBeforePerformPayment.await();

                    try {
                        if (reservationId != null) {
                            Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
                            TotalPrice totalPrice = priceAndDiscount.getLeft();
                            PaymentSpecification specification = new PaymentSpecification(reservationId, null, null, totalPrice.getPriceWithVAT(),
                                event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
                                "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
                            var paymentResult = ticketReservationManager.performPayment(specification, totalPrice, PaymentProxy.OFFLINE, StaticPaymentMethods.BANK_TRANSFER, null);
                            assertTrue(paymentResult.isSuccessful());
                        }
                    } catch (Throwable t) {
                    } finally {
                        countdownLatchBeforeComplete.countDown();
                    }


                    countdownLatchBeforeComplete.await();
                    if (reservationId != null) {
                        ticketReservationManager.confirmOfflinePayment(event, reservationId, null, eventAndUser.getRight());
                    }
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                } catch (Throwable t) {
                } finally {
                    doneSignal.countDown();
                }
            };
            new Thread(runnable).start();
        }

        doneSignal.await();

        Assertions.assertEquals(1, promoCodeDiscountRepository.countConfirmedPromoCode(promoCodeDiscount.getId()));
    }
}
