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
package alfio.controller.api.v2.user.reservation;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.v2.model.ReservationInfo;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class DiscountedReservationFlowIntegrationTest extends BaseReservationFlowTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;

    @Test
    void discountedInPersonEvent() throws Exception {
        super.testBasicFlow(() -> {
            List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
            );
            Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
            return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()), null, null, null, null, true, true);
        });
    }

    @Override
    protected void checkOrderSummary(ReservationInfo reservation, ReservationFlowContext context) {
        var orderSummary = reservation.getOrderSummary();
        assertTrue(orderSummary.isNotYetPaid());
        assertEquals("9.00", orderSummary.getTotalPrice());
        assertEquals("0.09", orderSummary.getTotalVAT());
        assertEquals("1.00", orderSummary.getVatPercentage());
    }

    protected void validatePayment(String eventName, String reservationIdentifier, ReservationFlowContext context) {
        Principal principal = mock(Principal.class);
        Mockito.when(principal.getName()).thenReturn(context.userId);
        var reservation = ticketReservationRepository.findReservationById(reservationIdentifier);
        assertEquals(900, reservation.getFinalPriceCts());
        assertEquals(1000, reservation.getSrcPriceCts());
        assertEquals(9, reservation.getVatCts());
        assertEquals(100, reservation.getDiscountCts());
        assertEquals(1, eventApiController.getPendingPayments(eventName, principal).size());
        assertEquals("OK", eventApiController.confirmPayment(eventName, reservationIdentifier, null, principal));
        assertEquals(0, eventApiController.getPendingPayments(eventName, principal).size());
        assertEquals(900, eventRepository.getGrossIncome(context.event.getId()));
    }

    @Override
    protected void checkDiscountUsage(String reservationId, int promoCodeId, ReservationFlowContext context) {
        var promoCodeUsage = promoCodeRequestManager.retrieveDetailedUsage(promoCodeId, context.event.getId());
        assertEquals(1, promoCodeUsage.size());
        var usageDetail = promoCodeUsage.get(0);
        assertEquals(PROMO_CODE, usageDetail.getPromoCode());
        assertEquals(1, usageDetail.getReservations().size());
        assertEquals(reservationId, usageDetail.getReservations().get(0).getId());
        assertEquals(1, usageDetail.getReservations().get(0).getTickets().size());
    }
}
