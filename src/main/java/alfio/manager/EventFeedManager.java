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
import alfio.model.EventFeed;
import alfio.model.EventFeedEntry;
import alfio.repository.EventDescriptionRepository;
import alfio.repository.user.OrganizationRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.BASE_URL;

@Component
public class EventFeedManager {

    static final String GLOBAL_FEED_TITLE = "alf.io - upcoming events";

    private final EventManager eventManager;
    private final EventDescriptionRepository eventDescriptionRepository;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;

    public EventFeedManager(EventManager eventManager,
                            EventDescriptionRepository eventDescriptionRepository,
                            OrganizationRepository organizationRepository,
                            ConfigurationManager configurationManager) {
        this.eventManager = eventManager;
        this.eventDescriptionRepository = eventDescriptionRepository;
        this.organizationRepository = organizationRepository;
        this.configurationManager = configurationManager;
    }

    public EventFeed globalFeed(SearchOptions searchOptions, String lang) {
        var events = eventManager.getPublishedEvents(searchOptions);
        return new EventFeed(GLOBAL_FEED_TITLE, baseUrl(ConfigurationLevel.system()), toEntries(events, lang));
    }

    public Optional<EventFeed> organizationFeed(String slug, String lang) {
        return organizationRepository.findBySlug(slug).map(organization -> {
            var searchOptions = SearchOptions.empty();
            searchOptions.setOrganizerSlug(slug);
            var events = eventManager.getPublishedEvents(searchOptions);
            return new EventFeed(organization.getName(), baseUrl(ConfigurationLevel.organization(organization.getId())), toEntries(events, lang));
        });
    }

    private List<EventFeedEntry> toEntries(List<Event> events, String lang) {
        return events.stream()
            .map(event -> new EventFeedEntry(event, description(event, lang), eventManager.getEventUrl(event)))
            .toList();
    }

    private String description(Event event, String lang) {
        return eventDescriptionRepository.findDescriptionByEventIdTypeAndLocale(event.getId(), EventDescription.EventDescriptionType.DESCRIPTION, resolveLanguage(event, lang))
            .orElse("");
    }

    private static String resolveLanguage(Event event, String lang) {
        var languages = event.getContentLanguages().stream().map(cl -> cl.locale().getLanguage()).toList();
        if (lang != null && languages.contains(lang)) {
            return lang;
        }
        return languages.isEmpty() ? "en" : languages.get(0);
    }

    private String baseUrl(ConfigurationLevel level) {
        // BASE_URL is not yet configured on a fresh installation: the feed must still be served
        return StringUtils.removeEnd(configurationManager.getFor(BASE_URL, level).getValueOrDefault(""), "/");
    }
}
