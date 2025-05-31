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
import alfio.manager.FileUploadManager;
import alfio.manager.SubscriptionManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.UploadBase64FileModification;
import alfio.model.subscription.MaxEntriesOverageDetails;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionUsageExceeded;
import alfio.model.subscription.SubscriptionUsageExceededForEvent;
import alfio.repository.SubscriptionRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.BaseIntegrationTest;
import alfio.util.Json;
import alfio.util.SqlUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

import static alfio.test.util.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class ReservationFlowWithSubscriptionIntegrationTest extends BaseReservationFlowTest {

    public static final String DEFAULT_CATEGORY_NAME = "default";
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private SubscriptionManager subscriptionManager;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private FileUploadManager fileUploadManager;
    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    private static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");

    private ReservationFlowContext context;

    @BeforeEach
    void createContext() {
        List<TicketCategoryModification> categories = Arrays.asList(
            new TicketCategoryModification(null, DEFAULT_CATEGORY_NAME, TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()),
            new TicketCategoryModification(null, "hidden", TicketCategory.TicketAccessType.INHERIT, 2,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.ONE, true, "", true, URL_CODE_HIDDEN, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        var uploadFileForm = new UploadBase64FileModification();
        uploadFileForm.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        uploadFileForm.setName("my-image.gif");
        uploadFileForm.setType("image/gif");
        String fileBlobId = fileUploadManager.insertFile(uploadFileForm);
        assertNotNull(fileBlobId);

        // create subscription descriptor

        var event = eventAndUser.getLeft();
        int maxEntries = 2;
        var descriptorId = createSubscriptionDescriptor(event.getOrganizationId(), fileUploadManager, subscriptionManager, maxEntries);
        var descriptor = subscriptionRepository.findOne(descriptorId).orElseThrow();
        var subscriptionIdAndPin = confirmAndLinkSubscription(descriptor, event.getOrganizationId(), subscriptionRepository, ticketReservationRepository, maxEntries);
        this.subscriptionRepository.linkSubscriptionAndEvent(descriptorId, event.getId(), 0, event.getOrganizationId(), null);
        var linkedSubscriptions = eventManager.getLinkedSubscriptionIds(event.getId(), event.getOrganizationId());
        assertEquals(List.of(descriptorId), linkedSubscriptions);
        this.context = new ReservationFlowContext(event, owner(eventAndUser.getRight()), subscriptionIdAndPin.getLeft(), subscriptionIdAndPin.getRight());
    }

    @AfterEach
    void deleteEvent() {
        try {
            eventManager.deleteEvent(context.event.getId(), context.userId);
        } catch(Exception ex) {
            //ignore exception because the transaction might be aborted
        }
    }

    @Test
    void inPersonEventWithSubscriptionUsingID() {
        var modifiedContext = new ReservationFlowContext(context.event, context.userId, context.subscriptionId, null);
        super.testAddSubscription(modifiedContext, 1, DEFAULT_CATEGORY_NAME);
        var eventInfo = eventStatisticsManager.getEventWithAdditionalInfo(context.event.getShortName(), context.userId);
        assertEquals(BigDecimal.ZERO, eventInfo.getGrossIncome());
        assertErrorWhenTransferToAnotherOrg();
    }

    @Test
    void inPersonEventWithSubscriptionUsingPin() {
        super.testAddSubscription(context, 1, DEFAULT_CATEGORY_NAME);
        assertErrorWhenTransferToAnotherOrg();
    }

    @Test
    void limitSubscriptionToSpecificCategory() {
        var newCategoryName = "new";
        var categoryRequest = new TicketCategoryModification(null, newCategoryName, TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
            new DateTimeModification(LocalDate.now(clockProvider.getClock()).minusDays(1), LocalTime.now(clockProvider.getClock())),
            new DateTimeModification(LocalDate.now(clockProvider.getClock()).plusDays(1), LocalTime.now(clockProvider.getClock())),
            DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty());
        var categoryResult = eventManager.insertCategory(context.event, categoryRequest, context.userId);
        assertTrue(categoryResult.isSuccess());
        int organizationId = context.event.getOrganizationId();
        var descriptorId = eventManager.getLinkedSubscriptionIds(context.event.getId(), organizationId).get(0);
        // remap link
        subscriptionRepository.linkSubscriptionAndEvent(descriptorId, context.event.getId(), 0, organizationId, List.of(categoryResult.getData()));
        super.testAddSubscription(context, 1, newCategoryName);
        assertErrorWhenTransferToAnotherOrg();
    }

    @Test
    void triggerMaxSubscriptionPerEvent() {
        super.testAddSubscription(context, 1, DEFAULT_CATEGORY_NAME);
        var params = Map.of("subscriptionId", context.subscriptionId, "eventId", context.event.getId());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from tickets_reservation where subscription_id_fk = :subscriptionId and event_id_fk = :eventId", params, Integer.class));
        int ticketId = ticketRepository.findFreeByEventId(context.event.getId()).get(0).getId();
        var exception = assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate.update("update ticket set subscription_id_fk = :subscriptionId where id = :id", Map.of("subscriptionId", context.subscriptionId, "id", ticketId)));
        var serverError = SqlUtils.findServerError(exception);
        assertTrue(serverError.isPresent());
        assertEquals(SubscriptionUsageExceededForEvent.ERROR, serverError.get().getMessage());
        assertNotNull(serverError.get().getDetail());
        var detail = Json.fromJson(serverError.get().getDetail(), MaxEntriesOverageDetails.class);
        assertEquals(1, detail.getAllowed());
        assertEquals(2, detail.getRequested());
    }

    @Test
    void triggerMaxUsage() {
        assertEquals(2, subscriptionRepository.findSubscriptionById(context.subscriptionId).getMaxEntries());
        jdbcTemplate.update("update subscription set max_entries = 1 where id = :id::uuid", Map.of("id", context.subscriptionId));
        super.testAddSubscription(context, 1, DEFAULT_CATEGORY_NAME);
        var params = Map.of("subscriptionId", context.subscriptionId, "eventId", context.event.getId());
        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from tickets_reservation where subscription_id_fk = :subscriptionId and event_id_fk = :eventId", params, Integer.class));
        int ticketId = ticketRepository.findFreeByEventId(context.event.getId()).get(0).getId();
        var exception = assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate.update("update ticket set subscription_id_fk = :subscriptionId where id = :id", Map.of("subscriptionId", context.subscriptionId, "id", ticketId)));
        var serverError = SqlUtils.findServerError(exception);
        assertTrue(serverError.isPresent());
        assertEquals(SubscriptionUsageExceeded.ERROR, serverError.get().getMessage());
        assertNotNull(serverError.get().getDetail());
        var detail = Json.fromJson(serverError.get().getDetail(), MaxEntriesOverageDetails.class);
        assertEquals(1, detail.getAllowed());
        assertEquals(2, detail.getRequested());
    }

    @Test
    void useSubscriptionToBuyMultipleTickets() {
        var subscriptionById = subscriptionRepository.findDescriptorBySubscriptionId(context.subscriptionId);
        assertEquals(2, subscriptionById.getMaxEntries());
        assertEquals(SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT, subscriptionById.getUsageType());
        jdbcTemplate.update("update subscription_descriptor set usage_type = 'UNLIMITED' where id = :id", Map.of("id", subscriptionById.getId()));
        super.testAddSubscription(context, 2, DEFAULT_CATEGORY_NAME);
        assertErrorWhenTransferToAnotherOrg();
    }

    @Test
    void unlinkSubscriptionAndTransferResources() {
        int eventId = context.event.getId();
        int orgId = context.event.getOrganizationId();
        subscriptionRepository.removeAllSubscriptionsForEvent(eventId, orgId);
        BaseIntegrationTest.testTransferEventToAnotherOrg(eventId, orgId, context.userId, jdbcTemplate);
        var descriptor = subscriptionRepository.findDescriptorBySubscriptionId(context.subscriptionId);
        BaseIntegrationTest.testTransferSubscriptionDescriptorToAnotherOrg(descriptor.getId(), orgId, context.userId, jdbcTemplate);
    }

    private void assertErrorWhenTransferToAnotherOrg() {
        var definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_NESTED);
        var template = new TransactionTemplate(platformTransactionManager, definition);
        template.execute(status -> {
            var savepoint = status.createSavepoint();
            try {
                var event = context.event;
                BaseIntegrationTest.testTransferEventToAnotherOrg(event.getId(), event.getOrganizationId(), context.userId, jdbcTemplate);
            } catch (UncategorizedSQLException uex) {
                var error = SqlUtils.findServerError(uex).orElseThrow();
                assertEquals("CANNOT_TRANSFER_SUBSCRIPTION_LINK", error.getMessage());
                status.rollbackToSavepoint(savepoint);
            }
            return null;
        });
        template.execute(status -> {
            var savepoint = status.createSavepoint();
            try {
                var descriptor = subscriptionRepository.findDescriptorBySubscriptionId(context.subscriptionId);
                BaseIntegrationTest.testTransferSubscriptionDescriptorToAnotherOrg(descriptor.getId(), descriptor.getOrganizationId(), context.userId, jdbcTemplate);
            } catch (UncategorizedSQLException uex) {
                var error = SqlUtils.findServerError(uex).orElseThrow();
                assertEquals("CANNOT_TRANSFER_SUBSCRIPTION_LINK", error.getMessage());
                status.rollbackToSavepoint(savepoint);
            }
            return null;
        });
    }

    @Test
    void testUpdateEventHeaderError() {
        List<TicketCategoryModification> categories = Collections.singletonList(
            new TicketCategoryModification(null, DEFAULT_CATEGORY_NAME, TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                new DateTimeModification(LocalDate.now(clockProvider.getClock()), LocalTime.now(clockProvider.getClock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        Pair<Event, String> pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        Event event = pair.getLeft();
        String username = pair.getRight();

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description new");
        desc.put("it", "muh description new");
        desc.put("de", "muh description new");

        var descriptorId = createSubscriptionDescriptor(event.getOrganizationId(), fileUploadManager, subscriptionManager, 10);
        this.subscriptionRepository.linkSubscriptionAndEvent(descriptorId, event.getId(), 0, event.getOrganizationId(), null);
        int newOrgId = BaseIntegrationTest.createNewOrg(username, jdbcTemplate);

        EventModification em = new EventModification(event.getId(),
            Event.EventFormat.IN_PERSON,
            "http://example.com/new",
            null,
            "http://example.com/tc",
            "http://example.com/pp",
            "https://example.com/img.png",
            null,
            event.getShortName(),
            "new display name",
            newOrgId,
            event.getLocation(),
            "0.0",
            "0.0",
            ZoneId.systemDefault().getId(),
            desc,
            DateTimeModification.fromZonedDateTime(event.getBegin()),
            DateTimeModification.fromZonedDateTime(event.getEnd().plusDays(42)),
            event.getRegularPrice(),
            event.getCurrency(),
            eventRepository.countExistingTickets(event.getId()),
            event.getVat(),
            event.isVatIncluded(),
            event.getAllowedPaymentProxies(),
            Collections.emptyList(),
            false,
            null,
            7,
            null,
            null,
            AlfioMetadata.empty(),
            List.of());

        assertThrows(IllegalArgumentException.class, () -> eventManager.updateEventHeader(event, em, username));
    }
}
