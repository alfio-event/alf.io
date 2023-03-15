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
import alfio.controller.IndexController;
import alfio.controller.api.ControllerConfiguration;
import alfio.controller.api.admin.AdditionalServiceApiController;
import alfio.controller.api.admin.CheckInApiController;
import alfio.controller.api.admin.EventApiController;
import alfio.controller.api.admin.UsersApiController;
import alfio.controller.api.v1.AttendeeApiController;
import alfio.controller.api.v2.InfoApiController;
import alfio.controller.api.v2.TranslationsApiController;
import alfio.controller.api.v2.user.EventApiV2Controller;
import alfio.controller.api.v2.user.ReservationApiV2Controller;
import alfio.controller.api.v2.user.TicketApiV2Controller;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.system.AdminJobExecutor;
import alfio.manager.system.AdminJobManager;
import alfio.manager.system.AdminJobManagerInvoker;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.ExtensionLog;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.metadata.TicketMetadataContainer;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.*;
import alfio.repository.audit.ScanAuditRepository;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static alfio.test.util.IntegrationTestUtil.*;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class RetryConfirmationFlowIntegrationTest extends BaseReservationFlowTest {

    private static final String TICKET_METADATA = "ticketMetadata";
    private static final String INVOICE_NUMBER_GENERATOR = "invoiceNumberGenerator";
    private final OrganizationRepository organizationRepository;
    private final UserManager userManager;
    private final AdminJobQueueRepository adminJobQueueRepository;
    private final AdminJobManagerInvoker adminJobManagerInvoker;

    @Autowired
    public RetryConfirmationFlowIntegrationTest(ConfigurationRepository configurationRepository,
                                                EventManager eventManager,
                                                EventRepository eventRepository,
                                                EventStatisticsManager eventStatisticsManager,
                                                TicketCategoryRepository ticketCategoryRepository,
                                                TicketReservationRepository ticketReservationRepository,
                                                EventApiController eventApiController,
                                                TicketRepository ticketRepository,
                                                TicketFieldRepository ticketFieldRepository,
                                                AdditionalServiceApiController additionalServiceApiController,
                                                SpecialPriceTokenGenerator specialPriceTokenGenerator,
                                                SpecialPriceRepository specialPriceRepository,
                                                CheckInApiController checkInApiController,
                                                AttendeeApiController attendeeApiController,
                                                UsersApiController usersApiController,
                                                ScanAuditRepository scanAuditRepository,
                                                AuditingRepository auditingRepository,
                                                AdminReservationManager adminReservationManager,
                                                TicketReservationManager ticketReservationManager,
                                                InfoApiController infoApiController,
                                                TranslationsApiController translationsApiController,
                                                EventApiV2Controller eventApiV2Controller,
                                                ReservationApiV2Controller reservationApiV2Controller,
                                                TicketApiV2Controller ticketApiV2Controller,
                                                IndexController indexController,
                                                NamedParameterJdbcTemplate jdbcTemplate,
                                                ExtensionLogRepository extensionLogRepository,
                                                ExtensionService extensionService,
                                                PollRepository pollRepository,
                                                ClockProvider clockProvider,
                                                NotificationManager notificationManager,
                                                UserRepository userRepository,
                                                OrganizationDeleter organizationDeleter,
                                                PromoCodeDiscountRepository promoCodeDiscountRepository,
                                                PromoCodeRequestManager promoCodeRequestManager,
                                                ExportManager exportManager,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                AdminJobQueueRepository adminJobQueueRepository,
                                                AdminJobManager adminJobManager) {
        super(configurationRepository,
            eventManager,
            eventRepository,
            eventStatisticsManager,
            ticketCategoryRepository,
            ticketReservationRepository,
            eventApiController,
            ticketRepository,
            ticketFieldRepository,
            additionalServiceApiController,
            specialPriceTokenGenerator,
            specialPriceRepository,
            checkInApiController,
            attendeeApiController,
            usersApiController,
            scanAuditRepository,
            auditingRepository,
            adminReservationManager,
            ticketReservationManager,
            infoApiController,
            translationsApiController,
            eventApiV2Controller,
            reservationApiV2Controller,
            ticketApiV2Controller,
            indexController,
            jdbcTemplate,
            extensionLogRepository,
            extensionService,
            pollRepository,
            clockProvider,
            notificationManager,
            userRepository,
            organizationDeleter,
            promoCodeDiscountRepository,
            promoCodeRequestManager,
            exportManager);
        this.organizationRepository = organizationRepository;
        this.userManager = userManager;
        this.adminJobQueueRepository = adminJobQueueRepository;
        this.adminJobManagerInvoker = new AdminJobManagerInvoker(adminJobManager);
    }

    private ReservationFlowContext createContext(boolean invoiceExtensionFailure) {
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
        return new CustomReservationFlowContext(eventAndUser.getLeft(), eventAndUser.getRight() + "_owner", invoiceExtensionFailure);
    }

    @Test
    void invoiceExtensionFailure() throws Exception {
        insertOrUpdateExtension("/retry-reservation/fail-invoice-number-generator.js", INVOICE_NUMBER_GENERATOR, false);
        insertOrUpdateExtension("/retry-reservation/success-ticket-metadata.js", TICKET_METADATA, false);
        super.testBasicFlow(() -> createContext(true));
    }

    @Test
    void metadataFailure() throws Exception {
        insertOrUpdateExtension("/retry-reservation/success-invoice-number-generator.js", INVOICE_NUMBER_GENERATOR, false);
        insertOrUpdateExtension("/retry-reservation/fail-ticket-metadata.js", TICKET_METADATA, false);
        super.testBasicFlow(() -> createContext(false));
    }

    @Override
    protected void ensureReservationIsComplete(String reservationId, ReservationFlowContext context) {
        var ctx = (CustomReservationFlowContext) context;
        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.PENDING, context);
        var reservation = ticketReservationRepository.findReservationById(reservationId);
        if (ctx.invoiceExtensionFailure) {
            // in this case the transaction must have been rolled back completely
            assertNull(reservation.getInvoiceNumber());
        } else {
            assertEquals("ABCD", reservation.getInvoiceNumber());
        }
        // check that the confirmation has been rescheduled
        var now = ZonedDateTime.now(clockProvider.getClock()).plusSeconds(3);
        var schedules = adminJobQueueRepository.loadPendingSchedules(Set.of(AdminJobExecutor.JobName.RETRY_RESERVATION_CONFIRMATION.name()), now);
        assertEquals(1, schedules.size());

        // fix the error, then trigger reschedule
        if (ctx.invoiceExtensionFailure) {
            insertOrUpdateExtension("/retry-reservation/success-invoice-number-generator.js", INVOICE_NUMBER_GENERATOR, true);
        } else {
            insertOrUpdateExtension("/retry-reservation/success-ticket-metadata.js", TICKET_METADATA, true);
        }
        adminJobManagerInvoker.invokeProcessPendingReservationsRetry(now);
        checkStatus(reservationId, HttpStatus.OK, true, TicketReservation.TicketReservationStatus.COMPLETE, context);
        reservation = ticketReservationRepository.findReservationById(reservationId);
        assertEquals("ABCD", reservation.getInvoiceNumber());
        ticketRepository.findTicketsInReservation(reservationId)
            .forEach(t -> {
                var metadata = ticketRepository.getTicketMetadata(t.getId()).getMetadataForKey(TicketMetadataContainer.GENERAL);
                assertTrue(metadata.isPresent());
                assertEquals(t.getUuid(), metadata.get().getAttributes().get("uuid"));
            });
    }

    @Override
    protected void assertEventLogged(List<ExtensionLog> extLog, ExtensionEvent event, int logSize) {
        super.assertEventLogged(extLog, event);
    }

    @Override
    protected void customizeContactFormForSuccessfulReservation(ContactAndTicketsForm contactForm) {
        contactForm.setInvoiceRequested(true);
        contactForm.setBillingAddressCity("City");
        contactForm.setBillingAddressCompany("Company");
        contactForm.setBillingAddressZip("0000");
        contactForm.setBillingAddressLine1("address");
        contactForm.setAddCompanyBillingDetails(true);
        contactForm.setVatCountryCode("CH");
        contactForm.setVatNr("1234567");
    }

    private void insertOrUpdateExtension(String filePath, String name, boolean update) {
        try (var extensionInputStream = requireNonNull(RetryConfirmationFlowIntegrationTest.class.getResourceAsStream(filePath))) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(extensionInputStream, StandardCharsets.UTF_8));
            String concatenation = String.join("\n", extensionStream);
            String previousPath = update ? "-" : null;
            String previousName = update ? name : null;
            extensionService.createOrUpdate(previousPath, previousName, new Extension("-", name, concatenation, true));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static class CustomReservationFlowContext extends ReservationFlowContext {

        private final boolean invoiceExtensionFailure;
        CustomReservationFlowContext(Event event, String userId, boolean invoiceExtensionFailure) {
            super(event, userId);
            this.invoiceExtensionFailure = invoiceExtensionFailure;
        }
    }
}
