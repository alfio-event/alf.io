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
import alfio.manager.payment.PaymentSpecification;
import alfio.manager.support.PaymentResult;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.repository.EventRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
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
import java.util.stream.Collectors;

import static alfio.model.EventCheckInInfo.VERSION_FOR_CODE_CASE_INSENSITIVE;
import static alfio.model.EventCheckInInfo.VERSION_FOR_LINKED_ADDITIONAL_SERVICE;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CheckInManagerIntegrationTest {

    @Autowired
    private CheckInManager checkInManager;
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
    private TicketRepository ticketRepository;
    @Autowired
    private AdditionalServiceManager additionalServiceManager;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;


    @Test
    void testReturnOnlyOnce() {
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
        var additionalServiceRequest = new EventModification.AdditionalService(
            null,
            BigDecimal.ONE,
            true,
            1,
            100,
            2,
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).minusDays(1)),
            DateTimeModification.fromZonedDateTime(ZonedDateTime.now(ClockProvider.clock()).plusDays(1)),
            BigDecimal.ZERO,
            AdditionalService.VatType.INHERITED,
            List.of(),
            List.of(new EventModification.AdditionalServiceText(null, "en", "bla", AdditionalServiceText.TextType.TITLE)),
            List.of(new EventModification.AdditionalServiceText(null, "en", "blabla", AdditionalServiceText.TextType.DESCRIPTION)),
            AdditionalService.AdditionalServiceType.SUPPLEMENT,
            AdditionalService.SupplementPolicy.OPTIONAL_UNLIMITED_AMOUNT
        );
        var additionalService = additionalServiceManager.insertAdditionalService(event, additionalServiceRequest);
        var category = ticketCategoryRepository.findAllTicketCategories(event.getId()).get(0);
        TicketReservationModification tr = new TicketReservationModification();
        tr.setQuantity(AVAILABLE_SEATS);
        tr.setTicketCategoryId(category.getId());

        var tickets = new TicketReservationWithOptionalCodeModification(tr, Optional.empty());
        var additionalServices = new AdditionalServiceReservationModification();
        additionalServices.setAdditionalServiceId(additionalService.getId());
        additionalServices.setQuantity(1);

        var additionalServicesModification = new ASReservationWithOptionalCodeModification(additionalServices, Optional.empty());
        String reservationId = ticketReservationManager.createTicketReservation(event, List.of(tickets), List.of(additionalServicesModification), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);
        Pair<TotalPrice, Optional<PromoCodeDiscount>> priceAndDiscount = ticketReservationManager.totalReservationCostWithVAT(reservationId);
        TotalPrice reservationCost = priceAndDiscount.getLeft();
        assertTrue(priceAndDiscount.getRight().isEmpty());
        PaymentSpecification specification = new PaymentSpecification(reservationId, null, reservationCost.getPriceWithVAT(),
            event, "email@example.com", new CustomerName("full name", "full", "name", event.mustUseFirstAndLastName()),
            "billing address", null, Locale.ENGLISH, true, false, null, "IT", "123456", PriceContainer.VatStatus.INCLUDED, true, false);
        PaymentResult result = ticketReservationManager.performPayment(specification, reservationCost, PaymentProxy.OFFLINE, PaymentMethod.BANK_TRANSFER, null);
        assertTrue(result.isSuccessful());
        ticketReservationManager.confirmOfflinePayment(event, reservationId, null, eventAndUser.getRight());

        var ticketsWithAdditionalServices = ticketsWithAdditionalServices(reservationId, event);
        //
        assertEquals(1, ticketsWithAdditionalServices.size());
        var firstTicket = ticketsWithAdditionalServices.get(0);
        assertEquals((int) ticketRepository.findFirstTicketIdInReservation(reservationId).orElseThrow(), firstTicket.getId());

        // disable link support
        jdbcTemplate.update("update event set version = :version where id = :id", new MapSqlParameterSource("id", event.getId()).addValue("version", VERSION_FOR_CODE_CASE_INSENSITIVE));
        ticketsWithAdditionalServices = ticketsWithAdditionalServices(reservationId, eventRepository.findById(event.getId()));
        firstTicket = ticketsWithAdditionalServices.get(0);
        //
        assertEquals(1, ticketsWithAdditionalServices.size());
        assertEquals((int) ticketRepository.findFirstTicketIdInReservation(reservationId).orElseThrow(), firstTicket.getId());

        // re-enable support
        jdbcTemplate.update("update event set version = :version where id = :id", new MapSqlParameterSource("id", event.getId()).addValue("version", VERSION_FOR_LINKED_ADDITIONAL_SERVICE));
        var ticketId = jdbcTemplate.queryForObject("select min(id) from ticket where tickets_reservation_id = :reservationId and id <> :ticketId", Map.of("reservationId", reservationId, "ticketId", firstTicket.getId()), Integer.class);
        assertNotNull(ticketId);
        jdbcTemplate.update("update additional_service_item set ticket_id_fk = :ticketId where tickets_reservation_uuid = :reservationId", Map.of("ticketId", ticketId, "reservationId", reservationId));

        // verify link works
        ticketsWithAdditionalServices = ticketsWithAdditionalServices(reservationId, eventRepository.findById(event.getId()));
        firstTicket = ticketsWithAdditionalServices.get(0);
        assertEquals(1, ticketsWithAdditionalServices.size());
        assertEquals(ticketId, firstTicket.getId());
    }

    @NotNull
    private List<Ticket> ticketsWithAdditionalServices(String reservationId, Event event) {
        var returnedAdditionalServices = ticketReservationManager.findTicketsInReservation(reservationId).stream()
            .filter(ticket -> !checkInManager.getAdditionalServicesForTicket(ticket, event).isEmpty())
            .collect(Collectors.toList());
        return returnedAdditionalServices;
    }

}