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
import alfio.model.Event;
import alfio.model.EventFeed;
import alfio.model.EventFeedEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static alfio.util.EventFeedTestData.mockEvent;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EventFeedApiV2ControllerTest {

    private EventFeedManager feedManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        feedManager = mock(EventFeedManager.class);
        // same converters as alfio.config.MvcConfiguration: there is no ByteArrayHttpMessageConverter
        var stringConverter = new StringHttpMessageConverter();
        stringConverter.setSupportedMediaTypes(List.of(MediaType.ALL));
        mockMvc = MockMvcBuilders.standaloneSetup(new EventFeedApiV2Controller(feedManager))
            .setMessageConverters(stringConverter)
            .build();
        var entry = new EventFeedEntry(mockEvent("conf", "Conf", "Zurich", Event.EventFormat.IN_PERSON), "", "https://tickets.example.org/event/conf/");
        var feed = new EventFeed("My Org", "https://tickets.example.org", List.of(entry));
        when(feedManager.globalFeed(any(SearchOptions.class), any())).thenReturn(feed);
        when(feedManager.organizationFeed(eq("my-org"), any())).thenReturn(Optional.of(feed));
        when(feedManager.organizationFeed(eq("unknown"), any())).thenReturn(Optional.empty());
    }

    @Test
    void globalICal() throws Exception {
        mockMvc.perform(get("/api/v2/public/events/feed.ics"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/calendar;charset=UTF-8"))
            .andExpect(content().string(containsString("BEGIN:VEVENT")))
            .andExpect(content().string(containsString("SUMMARY:Conf")));
    }

    @Test
    void globalRss() throws Exception {
        mockMvc.perform(get("/api/v2/public/events/feed.rss"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/rss+xml;charset=UTF-8"))
            .andExpect(content().string(containsString("<title>Conf</title>")));
    }

    @Test
    void globalFeedForwardsLanguageAndFilters() throws Exception {
        mockMvc.perform(get("/api/v2/public/events/feed.ics").param("lang", "it").param("tags", "java"))
            .andExpect(status().isOk());

        verify(feedManager).globalFeed(argThat(o -> List.of("java").equals(o.getTags())), eq("it"));
    }

    @Test
    void organizationICalAndRss() throws Exception {
        mockMvc.perform(get("/api/v2/public/organization/my-org/events/feed.ics"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/calendar;charset=UTF-8"));
        mockMvc.perform(get("/api/v2/public/organization/my-org/events/feed.rss"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/rss+xml;charset=UTF-8"));
    }

    @Test
    void unknownOrganizationIs404() throws Exception {
        mockMvc.perform(get("/api/v2/public/organization/unknown/events/feed.ics"))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/public/organization/unknown/events/feed.rss"))
            .andExpect(status().isNotFound());
    }
}
