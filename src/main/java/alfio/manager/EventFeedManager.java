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
import java.util.Map;
import java.util.Optional;

import static alfio.model.EventDescription.EventDescriptionType.DESCRIPTION;
import static alfio.model.system.ConfigurationKeys.BASE_URL;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

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
        var baseUrl = baseUrl();
        return new EventFeed(GLOBAL_FEED_TITLE, baseUrl, toEntries(events, lang, baseUrl));
    }

    public Optional<EventFeed> organizationFeed(String slug, String lang) {
        return organizationRepository.findBySlug(slug).map(organization -> {
            var searchOptions = SearchOptions.empty();
            searchOptions.setOrganizerSlug(slug);
            var events = eventManager.getPublishedEvents(searchOptions);
            var baseUrl = baseUrl();
            // link to the public organization page (see IndexController, "/o/*")
            return new EventFeed(organization.getName(), baseUrl + "/o/" + slug, toEntries(events, lang, baseUrl));
        });
    }

    private List<EventFeedEntry> toEntries(List<Event> events, String lang, String baseUrl) {
        if (events.isEmpty()) {
            return List.of();
        }
        // load all the descriptions with a single query, to avoid N+1 queries
        var descriptionsByEvent = eventDescriptionRepository.findByEventIdsAndType(events.stream().map(Event::getId).toList(), DESCRIPTION).stream()
            .collect(groupingBy(EventDescription::getEventId, toMap(EventDescription::getLocale, EventDescription::getDescription)));
        return events.stream()
            .map(event -> new EventFeedEntry(event,
                descriptionsByEvent.getOrDefault(event.getId(), Map.of()).getOrDefault(resolveLanguage(event, lang), ""),
                EventManager.buildEventUrl(baseUrl, event)))
            .toList();
    }

    private static String resolveLanguage(Event event, String lang) {
        var languages = event.getContentLanguages().stream().map(cl -> cl.locale().getLanguage()).toList();
        if (lang != null && languages.contains(lang)) {
            return lang;
        }
        return languages.isEmpty() ? "en" : languages.get(0);
    }

    private String baseUrl() {
        // BASE_URL can be defined only at system level, so it is read once for the whole feed.
        // It is not yet configured on a fresh installation: the feed must still be served
        return StringUtils.removeEnd(configurationManager.getFor(BASE_URL, ConfigurationLevel.system()).getValueOrDefault(""), "/");
    }
}
