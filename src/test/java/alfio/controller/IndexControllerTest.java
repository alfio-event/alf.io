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
    private MessageSourceManager messageSourceManager;
    private Json json;

    @BeforeEach
    void setUp() {
        eventLoader = mock(EventLoader.class);
        head = mock(Element.class);
        index = mock(Element.class);
        html = mock(Element.class);
        event = mock(EventWithAdditionalInfo.class);
        session = mock(HttpSession.class);
        json = mock(Json.class);
        messageSourceManager = mock(MessageSourceManager.class);
        when(messageSourceManager.getBundleAsMap(anyString(), anyBoolean(), anyString())).thenReturn(Map.of());
        when(eventLoader.loadEventInfo(anyString(), eq(session))).thenReturn(Optional.of(event));
        when(index.getElementsByTagName("html")).thenReturn(List.of(html));
        when(json.asJsonString(any())).thenReturn("{}");
    }

    @Nested
    @DisplayName("Event is present")
    class EventIsPresent {
        @Test
        void singleLanguage() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            IndexController.preloadTranslations("shortName", session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"));
            verify(html).setAttribute("lang", "it");
        }

        @Test
        void singleLanguageWithWrongParam() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("it", "")));
            IndexController.preloadTranslations("shortName", session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"));
            verify(html).setAttribute("lang", "it");
        }

        @Test
        void singleLanguageWithParam() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", "")));
            IndexController.preloadTranslations("shortName", session, eventLoader, head, messageSourceManager, index, json, "de");
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"));
            verify(html).setAttribute("lang", "de");
        }

        @Test
        void multipleLanguages() {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            IndexController.preloadTranslations("shortName", session, eventLoader, head, messageSourceManager, index, json, null);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("de"));
            verify(html).setAttribute("lang", "");
        }

        @ParameterizedTest
        @ValueSource(strings = {"it", "de"})
        void multipleLanguagesWithParam(String param) {
            when(event.getContentLanguages()).thenReturn(List.of(new Language("de", ""), new Language("it", "")));
            IndexController.preloadTranslations("shortName", session, eventLoader, head, messageSourceManager, index, json, param);
            verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq(param));
            verify(html).setAttribute("lang", "");
        }


    }

    @Test
    void preloadTranslationsEventNotPresent() {
        IndexController.preloadTranslations(null, session, eventLoader, head, messageSourceManager, index, json, null);
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("en"));
        verify(html).setAttribute("lang", "en");

        IndexController.preloadTranslations(null, session, eventLoader, head, messageSourceManager, index, json, "it");
        verify(messageSourceManager).getBundleAsMap(anyString(), eq(true), eq("it"));
        verify(html).setAttribute("lang", "it");
    }
}