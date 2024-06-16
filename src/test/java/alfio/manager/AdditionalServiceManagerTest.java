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

import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.AdditionalServiceTextRepository;
import alfio.repository.PurchaseContextFieldRepository;
import alfio.repository.TicketRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.mockito.Mockito.*;

public class AdditionalServiceManagerTest {
    private static final String EVENT_CURRENCY = "CAD";

    private AdditionalService additionalService;
    private AdditionalServiceItemRepository additionalServiceItemRepository;
    private AdditionalServiceRepository additionalServiceRepository;
    private AdditionalServiceTextRepository additionalServiceTextRepository;
    private Event event;
    private NamedParameterJdbcTemplate jdbcTemplate;
    private TicketRepository ticketRepository;
    private PurchaseContextFieldRepository purchaseContextFieldRepository;
    private ReservationCostCalculator reservationCostCalculator;

    @BeforeEach
    void init() {
        event = mock(Event.class);
        when(event.getCurrency()).thenReturn(EVENT_CURRENCY);

        additionalService = mock(AdditionalService.class);
        additionalServiceItemRepository = mock(AdditionalServiceItemRepository.class);
        additionalServiceRepository = mock(AdditionalServiceRepository.class);
        additionalServiceTextRepository = mock(AdditionalServiceTextRepository.class);
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        purchaseContextFieldRepository = mock(PurchaseContextFieldRepository.class);
        ticketRepository = mock(TicketRepository.class);
        reservationCostCalculator = mock(ReservationCostCalculator.class);
    }

    @ParameterizedTest
    @CsvSource({
        "-1",
        "1",
        "5"
    })
    public void testNonFixedPriceCommited(int availQuantity) {
        final PromoCodeDiscount DISCOUNT = null;
        final String RESERVATION_ID = "7ddadb25-18e8-4c72-9727-11f0bdfdb698";
        final int REQUESTED_QUANTITY = 1;
        final BigDecimal AMOUNT = new BigDecimal("5.74");
        final boolean IS_FIXED_PRICE = false;

        when(additionalService.availableQuantity()).thenReturn(availQuantity);
        when(additionalService.fixPrice()).thenReturn(IS_FIXED_PRICE);
        when(jdbcTemplate.batchUpdate(any(), any(SqlParameterSource[].class))).thenReturn(new int[]{1});

        final ArrayList<Integer> FREE_SERVICE_ITEMS = new ArrayList<>();
        FREE_SERVICE_ITEMS.add(1);
        when(additionalServiceItemRepository.lockExistingItems(anyInt(), anyInt())).thenReturn(FREE_SERVICE_ITEMS);

        AdditionalServiceManager additionalServiceManager = new AdditionalServiceManager(
            additionalServiceRepository,
            additionalServiceTextRepository,
            additionalServiceItemRepository,
            jdbcTemplate,
            ticketRepository,
            purchaseContextFieldRepository,
            reservationCostCalculator
        );

        additionalServiceManager.bookAdditionalServiceItems(
            REQUESTED_QUANTITY,
            AMOUNT,
            additionalService,
            event,
            DISCOUNT,
            RESERVATION_ID
        );

        ArgumentCaptor<String> templateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource[]> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource[].class);

        verify(jdbcTemplate).batchUpdate(templateCaptor.capture(), paramsCaptor.capture());

        SqlParameterSource[] paramList = paramsCaptor.getValue();
        var srcPriceVal = paramList[0].getValue("srcPriceCts");

        Assertions.assertEquals(paramList.length, REQUESTED_QUANTITY, "Number of Sql parameter sets should match number of services requested to book");

        // Price is inserted into DB as fixed-point integer (E.g, 3.74 -> 374)
        Assertions.assertEquals(srcPriceVal, AMOUNT.multiply(new BigDecimal("100")).intValue(), "Value of input amount should match commited srcPriceCts");
    }
}
