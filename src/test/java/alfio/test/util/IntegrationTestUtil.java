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
package alfio.test.util;

import alfio.manager.EventManager;
import alfio.manager.FileUploadManager;
import alfio.manager.SubscriptionManager;
import alfio.manager.user.UserManager;
import alfio.model.AllocationStatus;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.*;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.SubscriptionRepository;
import alfio.repository.TicketReservationRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTestUtil {

    public static final int AVAILABLE_SEATS = 20;

    public static final Map<String, Map<String, String>> DB_CONF = new HashMap<>();
    public static final Map<String, String> DESCRIPTION = Collections.singletonMap("en", "desc");
    public static final String BASE_URL = "http://localhost:8080";

    static {
        DB_CONF.put("PGSQL", generateDBConfig("jdbc:postgresql://localhost:5432/alfio", "postgres", "password"));
        DB_CONF.put("PGSQL-TRAVIS", generateDBConfig("jdbc:postgresql://localhost:5432/alfio", "alfio_user", "password"));
    }

    public static Map<String, String> generateDBConfig(String url, String username, String password) {
        Map<String, String> c = new HashMap<>();
        c.put("datasource.url", url);
        c.put("datasource.username", username);
        c.put("datasource.password", password);
        return c;
    }

    public static void ensureMinimalConfiguration(ConfigurationRepository configurationRepository) {
        configurationRepository.deleteByKey(ConfigurationKeys.BASE_URL.getValue());
        configurationRepository.deleteByKey(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue());

        configurationRepository.insert(ConfigurationKeys.BASE_URL.getValue(), BASE_URL, "");
        configurationRepository.insert(ConfigurationKeys.SUPPORTED_LANGUAGES.getValue(), "7", "");


        configurationRepository.deleteByKey(ConfigurationKeys.INVOICE_ADDRESS.getValue());
        configurationRepository.insert(ConfigurationKeys.INVOICE_ADDRESS.getValue(), "INVOICE_ADDRESS", "");
        configurationRepository.deleteByKey(ConfigurationKeys.VAT_NR.getValue());
        configurationRepository.insert(ConfigurationKeys.VAT_NR.getValue(), "42", "");
    }

    public static Pair<Event, String> initEvent(List<TicketCategoryModification> categories,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                EventManager eventManager,
                                                EventRepository eventRepository) {
        return initEvent(categories, organizationRepository, userManager, eventManager, eventRepository, null, Event.EventFormat.IN_PERSON);
    }

    public static Pair<Event, String> initEvent(List<TicketCategoryModification> categories,
                                                OrganizationRepository organizationRepository,
                                                UserManager userManager,
                                                EventManager eventManager,
                                                EventRepository eventRepository,
                                                List<EventModification.AdditionalService> additionalServices,
                                                Event.EventFormat eventFormat) {

        String organizationName = UUID.randomUUID().toString();
        String username = UUID.randomUUID().toString();
        String eventName = UUID.randomUUID().toString();

        var organizationModification = new OrganizationModification(null, organizationName, "email@example.com", "org", null, null);
        userManager.createOrganization(organizationModification);
        Organization organization = organizationRepository.findByName(organizationName).orElseThrow();
        userManager.insertUser(organization.getId(), username, "test", "test", "test@example.com", Role.OPERATOR, User.Type.INTERNAL);
        userManager.insertUser(organization.getId(), username+"_owner", "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL);

        LocalDateTime expiration = LocalDateTime.now(ClockProvider.clock()).plusDays(5).plusHours(1);

        Map<String, String> desc = new HashMap<>();
        desc.put("en", "muh description");
        desc.put("it", "muh description");
        desc.put("de", "muh description");

        EventModification em = new EventModification(null, eventFormat, "url", "url", "url", "privacy","url", null,
                eventName, "event display name", organization.getId(),
                "muh location", "0.0", "0.0", ClockProvider.clock().getZone().getId(), desc,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(5), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(expiration.toLocalDate(), expiration.toLocalTime()),
                BigDecimal.TEN, "CHF", AVAILABLE_SEATS, BigDecimal.ONE, true, Collections.singletonList(PaymentProxy.OFFLINE), categories, false, new LocationDescriptor("","","",""), 7, null, additionalServices, AlfioMetadata.empty(), List.of());
        eventManager.createEvent(em, username);
        Event event = eventManager.getSingleEvent(eventName, username);
        Assertions.assertEquals(AVAILABLE_SEATS, eventRepository.countExistingTickets(event.getId()).intValue());
        return Pair.of(event, username);
    }

    public static void initAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        userRepository.create(UserManager.ADMIN_USERNAME, "", "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
    }

    public static void removeAdminUser(UserRepository userRepository, AuthorityRepository authorityRepository) {
        authorityRepository.revokeAll(UserManager.ADMIN_USERNAME);
        userRepository.deleteUser(userRepository.findIdByUserName(UserManager.ADMIN_USERNAME).orElseThrow());
    }

    public static UUID createSubscriptionDescriptor(int organizationId,
                                                    FileUploadManager fileUploadManager,
                                                    SubscriptionManager subscriptionManager,
                                                    int maxEntries) {
        var uploadFileForm = new UploadBase64FileModification();
        uploadFileForm.setFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF);
        uploadFileForm.setName("my-image.gif");
        uploadFileForm.setType("image/gif");
        String fileBlobId = fileUploadManager.insertFile(uploadFileForm);
        var subscriptionModification = new SubscriptionDescriptorModification(null,
            Map.of("en", "title"),
            Map.of("en", "description"),
            42,
            ZonedDateTime.now(ClockProvider.clock()),
            null,
            BigDecimal.TEN,
            new BigDecimal("7.7"),
            PriceContainer.VatStatus.INCLUDED,
            "CHF",
            false,
            organizationId,
            maxEntries,
            SubscriptionDescriptor.SubscriptionValidityType.CUSTOM,
            null,
            null,
            ZonedDateTime.now(ClockProvider.clock()).minusDays(1),
            ZonedDateTime.now(ClockProvider.clock()).plusDays(42),
            SubscriptionDescriptor.SubscriptionUsageType.ONCE_PER_EVENT,
            "https://example.org",
            null,
            fileBlobId,
            List.of(PaymentProxy.STRIPE),
            ClockProvider.clock().getZone(),
            false);

        return subscriptionManager.createSubscriptionDescriptor(subscriptionModification).orElseThrow();
    }

    public static Pair<UUID, String> confirmAndLinkSubscription(SubscriptionDescriptor descriptor,
                                                                int organizationId,
                                                                SubscriptionRepository subscriptionRepository,
                                                                TicketReservationRepository ticketReservationRepository,
                                                                int maxEntries) {
        assertTrue(subscriptionRepository.updatePriceForSubscriptions(descriptor.getId(), descriptor.getPrice() + 1) > 0);
        var zoneId = ClockProvider.clock().getZone();
        var subscriptionId = subscriptionRepository.selectFreeSubscription(descriptor.getId()).orElseThrow();
        var subscriptionReservationId = UUID.randomUUID().toString();
        ticketReservationRepository.createNewReservation(subscriptionReservationId, ZonedDateTime.now(ClockProvider.clock()), Date.from(Instant.now(ClockProvider.clock())), null, "en", null, new BigDecimal("7.7"), true, "CHF", organizationId, null);
        subscriptionRepository.bindSubscriptionToReservation(subscriptionReservationId, descriptor.getPrice(), AllocationStatus.PENDING, subscriptionId);
        subscriptionRepository.confirmSubscription(subscriptionReservationId, AllocationStatus.ACQUIRED,
            "Test", "Mc Test", "tickettest@test.com", maxEntries,
            null, null, ZonedDateTime.now(ClockProvider.clock()), zoneId.toString());
        var subscription = subscriptionRepository.findSubscriptionById(subscriptionId);
        assertEquals(descriptor.getPrice(), subscription.getSrcPriceCts());
        return Pair.of(subscriptionId, subscription.getPin());
    }
}
