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
import alfio.manager.support.reservation.ReservationCostCalculator;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static alfio.util.MonetaryUtil.HUNDRED;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class PercentageAdditionalServicesIntegrationTest {
    @Autowired
    private ReservationCostCalculator reservationCostCalculator;
    @Autowired
    private TicketReservationManager reservationManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
    }

    @Nested
    class PercentageOnReservation {
        @Test
        void taxIncluded() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_RESERVATION, null, null);
            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + 100.00 for the additional service + 10% + VAT = 220.00
            Assertions.assertEquals(22000, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxIncludedMinPrice() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_RESERVATION, new BigDecimal("20.01"), null);
            var totalPrice = bookAndCalculatePrice(event);
            Assertions.assertEquals(22001, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxIncludedMaxPrice() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_RESERVATION, null, new BigDecimal("9.99"));
            var totalPrice = bookAndCalculatePrice(event);
            Assertions.assertEquals(20999, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxNotIncluded() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.NOT_INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_RESERVATION, null, null);
            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + additional item + 10% + VAT = 222.20
            Assertions.assertEquals(22220, totalPrice.getPriceWithVAT());
        }

    }

    @Nested
    class PercentageOnTickets {

        @Test
        void taxIncluded() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_FOR_TICKET, null, null);

            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + 10% + VAT = 110.00
            Assertions.assertEquals(21000, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxIncludedMinPrice() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_FOR_TICKET, new BigDecimal("10.01"), null);
            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + 10.01 = 110.01
            Assertions.assertEquals(21001, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxIncludedMaxPrice() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_FOR_TICKET, null, new BigDecimal("9.99"));
            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + 10.01 = 110.01
            Assertions.assertEquals(20999, totalPrice.getPriceWithVAT());
        }

        @Test
        void taxNotIncluded() {
            // percentage fee with no min/max
            var event = initData(PriceContainer.VatStatus.NOT_INCLUDED, AdditionalService.SupplementPolicy.MANDATORY_PERCENTAGE_FOR_TICKET, null, null);
            var totalPrice = bookAndCalculatePrice(event);
            // we expect a price of 100.00 for the ticket + 10% + VAT = 111.00
            Assertions.assertEquals(21210, totalPrice.getPriceWithVAT());
        }
    }


    private Event initData(PriceContainer.VatStatus eventVatStatus,
                           AdditionalService.SupplementPolicy supplementPolicy,
                           BigDecimal minPrice,
                           BigDecimal maxPrice) {

        var mandatory = new EventModification.AdditionalService(
            null,
            BigDecimal.TEN,
            false,
            1,
            -1,
            -1,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).minusDays(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1)),
            BigDecimal.ZERO,
            AdditionalService.VatType.INHERITED,
            List.of(),
            List.of(new EventModification.AdditionalServiceText(null, "en", "mandatory", AdditionalServiceText.TextType.TITLE)),
            List.of(new EventModification.AdditionalServiceText(null, "en", "mandatory description", AdditionalServiceText.TextType.DESCRIPTION)),
            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            supplementPolicy, minPrice, maxPrice
        );
        var optional = new EventModification.AdditionalService(
            null,
            new BigDecimal("100.00"),
            true,
            2,
            -1,
            -1,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).minusDays(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1)),
            BigDecimal.ZERO,
            AdditionalService.VatType.INHERITED,
            List.of(),
            List.of(new EventModification.AdditionalServiceText(null, "en", "optional", AdditionalServiceText.TextType.TITLE)),
            List.of(new EventModification.AdditionalServiceText(null, "en", "optional description", AdditionalServiceText.TextType.DESCRIPTION)),
            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT, null, null
        );
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, HUNDRED, false,
                "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(mandatory, optional), Event.EventFormat.IN_PERSON, eventVatStatus);

        return eventAndUser.getLeft();
    }

    private TotalPrice bookAndCalculatePrice(Event event) {
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(1);
        tr.setTicketCategoryId(category.getId());
        var paramSource = new MapSqlParameterSource("eventId", event.getId()).addValue("policy", AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT.name());
        int additionalServiceId = Objects.requireNonNull(jdbcTemplate.queryForObject("select id from additional_service where event_id_fk = :eventId and supplement_policy = :policy", paramSource, Integer.class));
        var additionalServices = new AdditionalServiceReservationModification();
        additionalServices.setAdditionalServiceId(additionalServiceId);
        additionalServices.setQuantity(1);


        var tickets = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        String reservationId = reservationManager.createTicketReservation(event, List.of(tickets), List.of(new ASReservationWithOptionalCodeModification(additionalServices, Optional.empty())), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = reservationCostCalculator.totalReservationCostWithVAT(reservationId);
        return priceAndDiscount.getLeft();
    }
}
