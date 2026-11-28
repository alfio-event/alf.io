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
package alfio.controller.api.v2;

import alfio.config.Initializer;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.system.ConfigurationManager;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.CustomResourceBundleMessageSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TranslationsApiControllerTest {

    private ConfigurationRepository configurationRepository;
    private MessageSourceManager messageSourceManager;

    @BeforeEach
    void setUp() {
        var source = new CustomResourceBundleMessageSource();
        source.setBasenames("alfio.i18n.public");
        source.setDefaultEncoding(StandardCharsets.UTF_8.displayName());
        source.setFallbackToSystemLocale(false);
        source.setAlwaysUseMessageFormat(true);
        configurationRepository = mock(ConfigurationRepository.class);
        when(configurationRepository.getSystemOverrideMessages()).thenReturn(Map.of("de", Map.of("common.apply", "overridden")));
        messageSourceManager = new MessageSourceManager(source, configurationRepository);
    }

    @Test
    void adminTranslationsIgnoreTheOverrides() {
        var response = controller().getAdminTranslations("de");

        assertEquals("Anwenden", response.getBody().get("common.apply"));
        verify(configurationRepository, never()).getSystemOverrideMessages();
    }

    @Test
    void adminTranslationsFallBackToEnglishAndAreReadyForTheFrontend() {
        var translations = controller().getAdminTranslations("de").getBody();

        // placeholders converted for i18next; the keys without German translation come from the English file
        assertEquals("{{0}} von {{1}} bis {{2}}", translations.get("event-days.same-day"));
        assertEquals("Save", translations.get("admin.common.save"));
        assertEquals("de-DE", translations.get("locale"));
    }

    @Test
    void inProductionTheBrowserCachesTheTranslationsUntilTheNextRelease() {
        var response = controller().getAdminTranslations("en");

        assertEquals("max-age=31536000, public, immutable", response.getHeaders().getCacheControl());
    }

    @Test
    void inDevelopmentTheBrowserAlwaysRevalidates() {
        var response = controller(Initializer.PROFILE_DEV).getAdminTranslations("en");

        assertEquals("no-cache", response.getHeaders().getCacheControl());
    }

    private TranslationsApiController controller(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new TranslationsApiController(messageSourceManager, mock(ConfigurationManager.class), mock(I18nManager.class), environment);
    }
}
