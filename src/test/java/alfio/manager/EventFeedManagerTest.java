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

import alfio.controller.form.SearchOptions;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventDescription;
import alfio.model.user.Organization;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.user.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.BASE_URL;
import static alfio.util.EventFeedTestData.mockEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventFeedManagerTest {

    private EventManager eventManager;
    private EventDescriptionRepository eventDescriptionRepository;
    private OrganizationRepository organizationRepository;
    private EventFeedManager feedManager;
    private Event event;

    @BeforeEach
    void setUp() {
        eventManager = mock(EventManager.class);
        eventDescriptionRepository = mock(EventDescriptionRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        var configurationManager = mock(ConfigurationManager.class);
        var baseUrl = mock(ConfigurationManager.MaybeConfiguration.class);
        when(baseUrl.getRequiredValue()).thenReturn("https://tickets.example.org/");
        when(configurationManager.getFor(eq(BASE_URL), any(ConfigurationLevel.class))).thenReturn(baseUrl);

        event = mockEvent("conf", "Conf", "Zurich", Event.EventFormat.IN_PERSON);
        when(eventManager.getEventUrl(event)).thenReturn("https://tickets.example.org/event/conf/");
        when(eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(anyInt(), eq(EventDescription.EventDescriptionType.DESCRIPTION), anyString()))
            .thenReturn(Optional.empty());

        feedManager = new EventFeedManager(eventManager, eventDescriptionRepository, organizationRepository, configurationManager);
    }

    @Test
    void globalFeedUsesPublishedEventsAndBaseUrl() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));
        when(eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, "en"))
            .thenReturn(Optional.of("English description"));

        var feed = feedManager.globalFeed(options, "en");

        assertEquals("https://tickets.example.org", feed.link());
        assertEquals(1, feed.entries().size());
        assertEquals("English description", feed.entries().get(0).description());
        assertEquals("https://tickets.example.org/event/conf/", feed.entries().get(0).eventUrl());
    }

    @Test
    void unsupportedLanguageFallsBackToFirstEventLanguage() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));
        when(eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, "en"))
            .thenReturn(Optional.of("English description"));

        var feed = feedManager.globalFeed(options, "xx");

        assertEquals("English description", feed.entries().get(0).description());
    }

    @Test
    void missingDescriptionBecomesEmptyString() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));

        var feed = feedManager.globalFeed(options, null);

        assertEquals("", feed.entries().get(0).description());
    }

    @Test
    void organizationFeedFiltersBySlug() {
        var organization = mock(Organization.class);
        when(organization.getId()).thenReturn(1);
        when(organization.getName()).thenReturn("My Org");
        when(organizationRepository.findBySlug("my-org")).thenReturn(Optional.of(organization));
        when(eventManager.getPublishedEvents(any(SearchOptions.class))).thenReturn(List.of(event));

        var feed = feedManager.organizationFeed("my-org", "en").orElseThrow();

        var captor = ArgumentCaptor.forClass(SearchOptions.class);
        verify(eventManager).getPublishedEvents(captor.capture());
        assertEquals("my-org", captor.getValue().getOrganizerSlug());
        assertEquals("My Org", feed.title());
        assertEquals(1, feed.entries().size());
    }

    @Test
    void unknownOrganizationReturnsEmpty() {
        when(organizationRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertTrue(feedManager.organizationFeed("nope", "en").isEmpty());
        verify(eventManager, never()).getPublishedEvents(any());
    }
}
