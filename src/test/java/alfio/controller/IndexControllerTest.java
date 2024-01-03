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
import alfio.manager.i18n.MessageSourceManager;
import alfio.util.Json;
import ch.digitalfondue.jfiveparse.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class IndexControllerTest {

    private EventLoader eventLoader;
    private Element head;
    private Element index;
    private Element html;
    private EventWithAdditionalInfo event;
    private HttpSession session;

    private ServletWebRequest request;
    private MessageSourceManager messageSourceManager;
    private Json json;

    @BeforeEach
    void setUp() {
        eventLoader = mock(EventLoader.class);
        request = mock(ServletWebRequest.class);
        head = mock(Element.class);
        index = mock(Element.class);
        html = mock(Element.class);
        event = mock(EventWithAdditionalInfo.class);
        session = mock(HttpSession.class);
        json = mock(Json.class);
        messageSourceManager = mock(MessageSourceManager.class);
        when(messageSourceManager.getBundleAsMap(anyString(), anyBoolean(), anyString(), same(MessageSourceManager.PUBLIC_FRONTEND))).thenReturn(Map.of());
        when(eventLoader.loadEventInfo(anyString(), eq(session))).thenReturn(Optional.of(event));
        when(index.getElementsByTagName("html")).thenReturn(List.of(html));
        when(json.asJsonString(any())).thenReturn("{}");
        when(request.getNativeRequest(HttpServletRequest.class)).thenReturn(new MockHttpServletRequest());
    }

    @Nested
    @DisplayName("Event is present")
    class EventIsPresent {
        @Test
        void singleLanguage() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            IndexController.preloadTranslations("shortName", request, session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "it");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("en"), same(MessageSourceManager.PUBLIC_FRONTEND)); //for non en language we preload also the fallback
        }

        @Test
        void singleLanguageWithWrongParam() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            IndexController.preloadTranslations("shortName", request, session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "it");
        }

        @Test
        void singleLanguageWithParam() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", "")));
            IndexController.preloadTranslations("shortName", request, session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "de");
        }

        @Test
        void multipleLanguages() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            IndexController.preloadTranslations("shortName", request, session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", "de");
        }

        @ParameterizedTest
        @ValueSource(strings = {"it", "de"})
        void multipleLanguagesWithParam(String param) {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            IndexController.preloadTranslations("shortName", request, session, eventLoader, head, messageSourceManager, index, json, param);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq(param), same(MessageSourceManager.PUBLIC_FRONTEND));
            verify(html).setAttribute("lang", param);
        }


    }

    @Test
    void preloadTranslationsEventNotPresent() {
        IndexController.preloadTranslations(null, request, session, eventLoader, head, messageSourceManager, index, json, null);
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("en"), same(MessageSourceManager.PUBLIC_FRONTEND));
        verify(html).setAttribute("lang", "en");

        IndexController.preloadTranslations(null, request, session, eventLoader, head, messageSourceManager, index, json, "it");
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"), same(MessageSourceManager.PUBLIC_FRONTEND));
        verify(html).setAttribute("lang", "it");
    }
}