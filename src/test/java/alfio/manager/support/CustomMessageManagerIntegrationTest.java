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
package alfio.manager.support;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.EventManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.ConfigurationModification;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.MessageModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.system.ConfigurationKeys;
import alfio.model.TicketCategory;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.AlfioIntegrationTest;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static alfio.test.util.IntegrationTestUtil.DESCRIPTION;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static alfio.test.util.IntegrationTestUtil.owner;
import static org.junit.jupiter.api.Assertions.*;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class CustomMessageManagerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CustomMessageManager customMessageManager;
    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private ConfigurationManager configurationManager;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private UserManager userManager;
    @Autowired
    private EventManager eventManager;
    @Autowired
    private EventRepository eventRepository;

    private Event event;
    private String username;

    @BeforeEach
    void setUp() {
        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        var categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, 10,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                DESCRIPTION, BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty()));
        var pair = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);
        event = pair.getLeft();
        username = pair.getRight();
    }

    @Test
    void htmlPreviewIsRenderedWithEscapedContent() {
        var preview = customMessageManager.generatePreview(event.getShortName(), Set.of(), List.of(message("Hello {{fullName}} <script>alert('xss')</script> **bold**")), username);
        var htmlPreview = htmlPreview(preview);
        var html = htmlPreview.get("en");
        assertNotNull(html);
        assertTrue(html.contains("Hello John Doe"));
        assertTrue(html.contains("<strong>bold</strong>"));
        assertTrue(html.contains(event.getDisplayName()));
        assertFalse(html.contains("<script>alert"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void htmlPreviewIsOmittedIfHtmlEmailsAreDisabled() {
        configurationManager.saveAllEventConfiguration(event.getId(), event.getOrganizationId(),
            List.of(new ConfigurationModification(null, ConfigurationKeys.ENABLE_HTML_EMAILS.name(), "false")), owner(username));
        var preview = customMessageManager.generatePreview(event.getShortName(), Set.of(), List.of(message("Hello {{fullName}}")), username);
        assertTrue(htmlPreview(preview).isEmpty());
        @SuppressWarnings("unchecked")
        var messages = (List<MessageModification>) preview.get("preview");
        assertEquals("Hello John Doe", messages.getFirst().getTextExample());
    }

    @Test
    void htmlPreviewIncludesWalletLinksIfTicketIsAttached() {
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_WALLET, "true");
        configurationManager.saveSystemConfiguration(ConfigurationKeys.ENABLE_PASS, "true");
        var attachTicket = new MessageModification(Locale.ENGLISH, "Subject", "Hello {{fullName}}", null, null, true);
        var html = htmlPreview(customMessageManager.generatePreview(event.getShortName(), Set.of(), List.of(attachTicket), username)).get("en");
        assertNotNull(html);
        assertTrue(html.contains("/api/wallet/event/" + event.getShortName() + "/v1/version/passes/TICKETID"));
        assertTrue(html.contains("/api/pass/event/" + event.getShortName() + "/v1/version/passes/TICKETID"));
        // wallet links are not displayed if the ticket is not attached
        html = htmlPreview(customMessageManager.generatePreview(event.getShortName(), Set.of(), List.of(message("Hello {{fullName}}")), username)).get("en");
        assertFalse(html.contains("/v1/version/passes/"));
    }

    private static MessageModification message(String text) {
        return new MessageModification(Locale.ENGLISH, "Subject", text, null, null, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> htmlPreview(Map<String, Object> preview) {
        return (Map<String, String>) preview.get("htmlPreview");
    }
}
