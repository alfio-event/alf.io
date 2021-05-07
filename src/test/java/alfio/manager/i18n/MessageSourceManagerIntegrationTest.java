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
package alfio.manager.i18n;


import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.EventManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.TicketCategory;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import alfio.util.ClockProvider;
import alfio.util.Json;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static alfio.test.util.IntegrationTestUtil.AVAILABLE_SEATS;
import static alfio.test.util.IntegrationTestUtil.initEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class MessageSourceManagerIntegrationTest extends BaseIntegrationTest {


    @Autowired
    private MessageSourceManager messageSourceManager;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private Json json;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserManager userManager;

    @Autowired
    private EventManager eventManager;

    @Autowired
    private EventRepository eventRepository;

    private Event event;

    public void ensureConfiguration() {

        IntegrationTestUtil.ensureMinimalConfiguration(configurationRepository);
        List<TicketCategoryModification> categories = List.of(
            new TicketCategoryModification(null, "default", TicketCategory.TicketAccessType.INHERIT, AVAILABLE_SEATS,
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).minusDays(1), LocalTime.now(ClockProvider.clock())),
                new DateTimeModification(LocalDate.now(ClockProvider.clock()).plusDays(1), LocalTime.now(ClockProvider.clock())),
                Map.of("en", "desc"), BigDecimal.TEN, false, "", false, null, null, null, null, null, 0, null, null, AlfioMetadata.empty())
        );
        Pair<Event, String> eventAndUser = initEvent(categories, organizationRepository, userManager, eventManager, eventRepository);

        event = eventAndUser.getKey();
    }


    @Test
    public void testRootOverride() {

        assertEquals("VAT", messageSourceManager.getRootMessageSource().getMessage("common.vat", null, Locale.ENGLISH));

        configurationRepository.insert("TRANSLATION_OVERRIDE", json.asJsonString(Map.of("en", Map.of("common.vat", "GST"))), "");

        assertEquals("GST", messageSourceManager.getRootMessageSource().getMessage("common.vat", null, Locale.ENGLISH));
    }

    @Test
    public void testEventOverride() {
        ensureConfiguration();
        assertEquals("VAT", messageSourceManager.getMessageSourceFor(event).getMessage("common.vat", null, Locale.ENGLISH));

        configurationRepository.insert("TRANSLATION_OVERRIDE", json.asJsonString(Map.of("en", Map.of("common.vat", "SYSTEM.vat"))), "");
        assertEquals("SYSTEM.vat", messageSourceManager.getMessageSourceFor(event).getMessage("common.vat", null, Locale.ENGLISH));

        configurationRepository.insertOrganizationLevel(event.getOrganizationId(), "TRANSLATION_OVERRIDE", json.asJsonString(Map.of("en", Map.of("common.vat", "ORG.vat {0}"))), "");
        assertEquals("ORG.vat 42", messageSourceManager.getMessageSourceFor(event).getMessage("common.vat", new String[] {"42"}, Locale.ENGLISH));


        configurationRepository.insertEventLevel(event.getOrganizationId(), event.getId(),"TRANSLATION_OVERRIDE", json.asJsonString(Map.of("en", Map.of("common.vat", "EVENT.vat"))), "");
        assertEquals("EVENT.vat", messageSourceManager.getMessageSourceFor(event).getMessage("common.vat", null, Locale.ENGLISH));
    }


}
