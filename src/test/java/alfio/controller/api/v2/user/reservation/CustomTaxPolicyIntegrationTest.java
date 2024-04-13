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
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.extension.ExtensionService;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.validation.BeanPropertyBindingResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static alfio.controller.api.v2.user.reservation.BaseReservationFlowTest.*;
import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CustomTaxPolicyIntegrationTest {

    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;
    private final ExtensionService extensionService;
    private final ClockProvider clockProvider;
    private final EventManager eventManager;
    private final EventRepository eventRepository;
    private final ConfigurationRepository configurationRepository;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final ReservationApiV2Controller reservationApiV2Controller;
    private final TicketRepository ticketRepository;

    @Autowired
    public CustomTaxPolicyIntegrationTest(OrganizationRepository organizationRepository,
                                          EventManager eventManager,
                                          EventRepository eventRepository,
                                          UserManager userManager,
                                          ClockProvider clockProvider,
                                          ConfigurationRepository configurationRepository,
                                          TicketCategoryRepository ticketCategoryRepository,
                                          TicketRepository ticketRepository,
                                          TicketReservationManager ticketReservationManager,
                                          ReservationApiV2Controller reservationApiV2Controller,
                                          ExtensionService extensionService) {
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
        this.extensionService = extensionService;
        this.clockProvider = clockProvider;
        this.eventManager = eventManager;
        this.eventRepository = eventRepository;
        this.configurationRepository = configurationRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.reservationApiV2Controller = reservationApiV2Controller;
        this.ticketRepository = ticketRepository;
    }

    private ReservationFlowContext createContext(PriceContainer.VatStatus vatStatus) {
        try {
            IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
            insertExtension(extensionService, "/custom-tax-policy-extension.js", false, true, allEvents());
            List<TicketCategoryModification> categories = Arrays.asList(
                new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, new BigDecimal("100.00"), false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
                new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                    new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                    DESCRIPTION, new BigDecimal("10.00"), true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
            );
            Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, List.of(), Event.EventFormat.IN_PERSON, vatStatus);
            return new ReservationFlowContext(eventAndUser.getLeft(), owner(eventAndUser.getRight()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void triggerCustomTaxPolicyTaxIncluded() {
        var context = createContext(PriceContainer.VatStatus.INCLUDED);
        var categories = ticketCategoryRepository.findAllTicketCategories(context.event.getId());
        assertEquals(2, categories.size());
        var ticketRequest = new TicketReservationModification();
        ticketRequest.setQuantity(2);
        ticketRequest.setTicketCategoryId(categories.get(0).getId());

        var request = List.of(new TicketReservationWithOptionalCodeModification(ticketRequest, Optional.empty()));
        var reservationId = ticketReservationManager.createTicketReservation(context.event, request, List.of(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        var totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
        assertEquals(20000, totalPrice.getPriceWithVAT());

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(2, tickets.size());
        var firstUuid = tickets.get(0).getUuid();
        var secondUuid = tickets.get(1).getUuid();
        var contactAndTicketsForm = new ContactAndTicketsForm();
        contactAndTicketsForm.setFirstName("The");
        contactAndTicketsForm.setLastName("Customer");
        contactAndTicketsForm.setEmail("email@customer.com");
        contactAndTicketsForm.setTickets(Map.of(
            firstUuid, updateTicketOwnerForm("example@example.org"),
            secondUuid, updateTicketOwnerForm("example@example1.org")
        ));
        var bindingResult = new BeanPropertyBindingResult(contactAndTicketsForm, "form");
        var response = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactAndTicketsForm, bindingResult, null);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        // verify that first ticket has the expected tax settings
        assertEquals(PriceContainer.VatStatus.CUSTOM_INCLUDED_EXEMPT, ticketRepository.findByUUID(firstUuid).getVatStatus());
        assertEquals(PriceContainer.VatStatus.INCLUDED, ticketRepository.findByUUID(secondUuid).getVatStatus());
        totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
        assertEquals(19901, totalPrice.getPriceWithVAT());
        assertEquals(19901, ticketReservationManager.findById(reservationId).orElseThrow().getFinalPriceCts());
    }

    @Test
    void triggerCustomTaxPolicyTaxNotIncluded() {
        var context = createContext(PriceContainer.VatStatus.NOT_INCLUDED);
        var categories = ticketCategoryRepository.findAllTicketCategories(context.event.getId());
        assertEquals(2, categories.size());
        var ticketRequest = new TicketReservationModification();
        ticketRequest.setQuantity(2);
        ticketRequest.setTicketCategoryId(categories.get(0).getId());

        var request = List.of(new TicketReservationWithOptionalCodeModification(ticketRequest, Optional.empty()));
        var reservationId = ticketReservationManager.createTicketReservation(context.event, request, List.of(), DateUtils.addDays(new Date(), 1), Optional.empty(), Locale.ENGLISH, false, null);

        var totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
        assertEquals(20200, totalPrice.getPriceWithVAT());

        var tickets = ticketRepository.findTicketsInReservation(reservationId);
        assertEquals(2, tickets.size());
        var firstUuid = tickets.get(0).getUuid();
        var secondUuid = tickets.get(1).getUuid();
        var contactAndTicketsForm = new ContactAndTicketsForm();
        contactAndTicketsForm.setFirstName("The");
        contactAndTicketsForm.setLastName("Customer");
        contactAndTicketsForm.setEmail("email@customer.com");
        contactAndTicketsForm.setTickets(Map.of(
            firstUuid, updateTicketOwnerForm("example@example.org"),
            secondUuid, updateTicketOwnerForm("example@example1.org")
        ));
        var bindingResult = new BeanPropertyBindingResult(contactAndTicketsForm, "form");
        var response = reservationApiV2Controller.validateToOverview(reservationId, "en", false, contactAndTicketsForm, bindingResult, null);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        // verify that first ticket has the expected tax settings
        assertEquals(PriceContainer.VatStatus.CUSTOM_NOT_INCLUDED_EXEMPT, ticketRepository.findByUUID(firstUuid).getVatStatus());
        assertEquals(PriceContainer.VatStatus.NOT_INCLUDED, ticketRepository.findByUUID(secondUuid).getVatStatus());
        totalPrice = ticketReservationManager.totalReservationCostWithVAT(reservationId).getLeft();
        assertEquals(20100, totalPrice.getPriceWithVAT());
        assertEquals(20100, ticketReservationManager.findById(reservationId).orElseThrow().getFinalPriceCts());
    }

    private static UpdateTicketOwnerForm updateTicketOwnerForm(String email) {
        var form = new UpdateTicketOwnerForm();
        form.setFirstName("first");
        form.setLastName("last");
        form.setEmail(email);
        form.setUserLanguage("en");
        return form;
    }
}
