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
package alfio.controller;

import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.api.v2.model.Language;
import alfio.controller.api.v2.user.support.EventLoader;
import alfio.controller.support.DataPreloaderManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.model.Event;
import alfio.util.Json;
import ch.digitalfondue.jfiveparse.Element;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static alfio.test.util.TestUtil.FIXED_TIME_CLOCK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IndexControllerTest {

    private EventLoader eventLoader;
    private Element head;
    private Element index;
    private Element html;
    private EventWithAdditionalInfo eventInfo;
    private Event event;
    private HttpSession session;

    private ServletWebRequest request;
    private MessageSourceManager messageSourceManager;
    private Json json;

    @BeforeEach
    void setUp() {
        eventLoader = mock(EventLoader.class);
        request = mock(ServletWebRequest.class);
        head = new Element("head");
        index = mock(Element.class);
        html = mock(Element.class);
        eventInfo = mock(EventWithAdditionalInfo.class);
        event = mock(Event.class);
        session = mock(HttpSession.class);
        json = mock(Json.class);
        messageSourceManager = mock(MessageSourceManager.class);
        when(messageSourceManager.getBundleAsMap(anyString(), anyBoolean(), anyString(), same(MessageSourceManager.PUBLIC_FRONTEND))).thenReturn(Map.of());
        when(eventLoader.loadEventInfo(anyString(), eq(session))).thenReturn(Optional.of(eventInfo));
        when(eventInfo.purchaseContext()).thenReturn(event);
        when(event.getEnd()).thenReturn(ZonedDateTime.now(FIXED_TIME_CLOCK.getClock()).plusSeconds(1));
        when(index.getElementsByTagName("html")).thenReturn(List.of(html));
        when(json.asJsonString(any())).thenReturn("{}");
        when(request.getNativeRequest(HttpServletRequest.class)).thenReturn(new MockHttpServletRequest());
    }

    @Nested
    @DisplayName("Event is present")
    class EventIsPresent {
        @Test
        void singleLanguage() {
            when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "it");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("en"), same(MessageSourceManager.PUBLIC_FRONTEND)); //for non en language we preload also the fallback
        }

        @Test
        void singleLanguageWithWrongParam() {
            when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "it");
        }

        @Test
        void singleLanguageWithParam() {
            when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("de", "")));
            DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "de");
        }

        @Test
        void multipleLanguages() {
            when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "de");
        }

        @ParameterizedTest
        @ValueSource(strings = {"it", "de"})
        void multipleLanguagesWithParam(String param) {
            when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, param);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq(param), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", param);
        }

        @AfterEach
        void tearDown() {
            head.getElementsByTagName("meta")
                .forEach(n -> assertNotEquals("robots", n.getAttribute("name")));
        }
    }

    @Test
    void preloadTranslationsEventNotPresent() {
        DataPreloaderManager.preloadEventData(null, request, session, eventLoader, head, messageSourceManager, index, json, null);
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("en"), same(MessageSourceManager.PUBLIC_FRONTEND));
        verify(html).setAttribute("lang", "en");

        DataPreloaderManager.preloadEventData(null, request, session, eventLoader, head, messageSourceManager, index, json, "it");
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
        verify(html).setAttribute("lang", "it");
    }

    @Test
    void checkMetaNoIndexWhenEventExpired() {
        when(eventInfo.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
        when(event.getEnd()).thenReturn(ZonedDateTime.now(FIXED_TIME_CLOCK.getClock()).minusSeconds(1));
        DataPreloaderManager.preloadEventData("shortName", request, session, eventLoader, head, messageSourceManager, index, json, null);
        var robotsNodes = head.getElementsByTagName("meta").stream().filter(n -> "robots".equals(n.getAttribute("name"))).collect(Collectors.toList());
        assertEquals(1, robotsNodes.size());
        assertEquals("noindex", robotsNodes.get(0).getAttribute("content"));
    }
}