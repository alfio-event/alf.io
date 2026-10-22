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
package alfio.controller.api.v2.user;

import alfio.controller.form.SearchOptions;
import alfio.manager.EventFeedManager;
import alfio.model.EventFeed;
import alfio.util.EventFeedWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Public iCal and RSS feeds of upcoming events, see https://github.com/alfio-event/alf.io/issues/1425
 */
@RestController
@RequestMapping("/api/v2/public/")
public class EventFeedApiV2Controller {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);
    private static final MediaType RSS = new MediaType("application", "rss+xml", StandardCharsets.UTF_8);

    private final EventFeedManager eventFeedManager;

    public EventFeedApiV2Controller(EventFeedManager eventFeedManager) {
        this.eventFeedManager = eventFeedManager;
    }

    @GetMapping("events/feed.ics")
    public ResponseEntity<String> globalICal(SearchOptions searchOptions,
                                             @RequestParam(value = "lang", required = false) String lang) {
        return iCal(eventFeedManager.globalFeed(searchOptions, lang));
    }

    @GetMapping("events/feed.rss")
    public ResponseEntity<String> globalRss(SearchOptions searchOptions,
                                            @RequestParam(value = "lang", required = false) String lang) {
        return rss(eventFeedManager.globalFeed(searchOptions, lang));
    }

    @GetMapping("organization/{slug}/events/feed.ics")
    public ResponseEntity<String> organizationICal(@PathVariable String slug,
                                                   @RequestParam(value = "lang", required = false) String lang) {
        return eventFeedManager.organizationFeed(slug, lang)
            .map(this::iCal)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("organization/{slug}/events/feed.rss")
    public ResponseEntity<String> organizationRss(@PathVariable String slug,
                                                  @RequestParam(value = "lang", required = false) String lang) {
        return eventFeedManager.organizationFeed(slug, lang)
            .map(this::rss)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> iCal(EventFeed feed) {
        return EventFeedWriter.toICal(feed)
            .map(body -> ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"events.ics\"")
                .body(new String(body, StandardCharsets.UTF_8)))
            .orElseGet(() -> ResponseEntity.internalServerError().build());
    }

    private ResponseEntity<String> rss(EventFeed feed) {
        return ResponseEntity.ok()
            .contentType(RSS)
            .body(new String(EventFeedWriter.toRss(feed), StandardCharsets.UTF_8));
    }
}
