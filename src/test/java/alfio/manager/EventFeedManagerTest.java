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

import static alfio.model.EventDescription.EventDescriptionType.DESCRIPTION;
import static alfio.model.system.ConfigurationKeys.BASE_URL;
import static alfio.util.EventFeedTestData.mockEvent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventFeedManagerTest {

    private EventManager eventManager;
    private EventDescriptionRepository eventDescriptionRepository;
    private OrganizationRepository organizationRepository;
    private ConfigurationManager configurationManager;
    private EventFeedManager feedManager;
    private Event event;

    @BeforeEach
    void setUp() {
        eventManager = mock(EventManager.class);
        eventDescriptionRepository = mock(EventDescriptionRepository.class);
        organizationRepository = mock(OrganizationRepository.class);
        configurationManager = mock(ConfigurationManager.class);
        var baseUrl = mock(ConfigurationManager.MaybeConfiguration.class);
        when(baseUrl.getValueOrDefault(anyString())).thenReturn("https://tickets.example.org/");
        when(configurationManager.getFor(eq(BASE_URL), any(ConfigurationLevel.class))).thenReturn(baseUrl);

        event = mockEvent("conf", "Conf", "Zurich", Event.EventFormat.IN_PERSON);
        when(eventDescriptionRepository.findByEventIdsAndType(anyCollection(), eq(DESCRIPTION))).thenReturn(List.of());

        feedManager = new EventFeedManager(eventManager, eventDescriptionRepository, organizationRepository, configurationManager);
    }

    private static EventDescription description(Event event, String locale, String text) {
        return new EventDescription(event.getId(), locale, DESCRIPTION, text);
    }

    @Test
    void globalFeedUsesPublishedEventsAndBaseUrl() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));
        var descriptions = List.of(description(event, "en", "English description"), description(event, "it", "Descrizione"));
        when(eventDescriptionRepository.findByEventIdsAndType(List.of(event.getId()), DESCRIPTION))
            .thenReturn(descriptions);

        var feed = feedManager.globalFeed(options, "en");

        assertEquals("https://tickets.example.org", feed.link());
        assertEquals(1, feed.entries().size());
        assertEquals("English description", feed.entries().get(0).description());
        assertEquals("https://tickets.example.org/event/conf/", feed.entries().get(0).eventUrl());
    }

    @Test
    void requestedLanguageIsUsedWhenAvailable() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));
        var descriptions = List.of(description(event, "en", "English description"), description(event, "it", "Descrizione"));
        when(eventDescriptionRepository.findByEventIdsAndType(List.of(event.getId()), DESCRIPTION))
            .thenReturn(descriptions);

        var feed = feedManager.globalFeed(options, "it");

        assertEquals("Descrizione", feed.entries().get(0).description());
    }

    @Test
    void unsupportedLanguageFallsBackToFirstEventLanguage() {
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event));
        var descriptions = List.of(description(event, "en", "English description"));
        when(eventDescriptionRepository.findByEventIdsAndType(List.of(event.getId()), DESCRIPTION))
            .thenReturn(descriptions);

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
    void descriptionsAndBaseUrlAreLoadedOnceForAllEvents() {
        var other = mockEvent("other", "Other", "Lugano", Event.EventFormat.IN_PERSON);
        var options = SearchOptions.empty();
        when(eventManager.getPublishedEvents(options)).thenReturn(List.of(event, other));
        var descriptions = List.of(description(event, "en", "Conf description"), description(other, "en", "Other description"));
        when(eventDescriptionRepository.findByEventIdsAndType(List.of(event.getId(), other.getId()), DESCRIPTION))
            .thenReturn(descriptions);

        var feed = feedManager.globalFeed(options, "en");

        assertEquals("Conf description", feed.entries().get(0).description());
        assertEquals("Other description", feed.entries().get(1).description());
        assertEquals("https://tickets.example.org/event/other/", feed.entries().get(1).eventUrl());
        verify(eventDescriptionRepository, times(1)).findByEventIdsAndType(anyCollection(), eq(DESCRIPTION));
        verify(eventDescriptionRepository, never()).findDescriptionByEventIdTypeAndLocale(anyInt(), any(), anyString());
        verify(configurationManager, times(1)).getFor(eq(BASE_URL), any(ConfigurationLevel.class));
        verify(eventManager, never()).getEventUrl(any());
    }

    @Test
    void noDescriptionQueryWithoutEvents() {
        when(eventManager.getPublishedEvents(any(SearchOptions.class))).thenReturn(List.of());

        var feed = feedManager.globalFeed(SearchOptions.empty(), "en");

        assertTrue(feed.entries().isEmpty());
        verify(eventDescriptionRepository, never()).findByEventIdsAndType(anyCollection(), any());
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
        assertEquals("https://tickets.example.org/o/my-org", feed.link());
        assertEquals(1, feed.entries().size());
        assertEquals("https://tickets.example.org/event/conf/", feed.entries().get(0).eventUrl());
    }

    @Test
    void globalFeedWorksWhenBaseUrlIsNotConfigured() {
        when(configurationManager.getFor(eq(BASE_URL), any(ConfigurationLevel.class)))
            .thenReturn(new ConfigurationManager.MaybeConfiguration(BASE_URL));
        when(eventManager.getPublishedEvents(any(SearchOptions.class))).thenReturn(List.of());

        var feed = feedManager.globalFeed(SearchOptions.empty(), null);

        assertEquals("", feed.link());
        assertTrue(feed.entries().isEmpty());
    }

    @Test
    void unknownOrganizationReturnsEmpty() {
        when(organizationRepository.findBySlug("nope")).thenReturn(Optional.empty());

        assertTrue(feedManager.organizationFeed("nope", "en").isEmpty());
        verify(eventManager, never()).getPublishedEvents(any());
    }
}
