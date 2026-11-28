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
package alfio.controller.support;

import alfio.model.ContentLanguage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSources;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test checks if all the date patterns for all defined languages are correct, in order to spot errors at build
 * time.
 */
class FormattersTest {

    private static final List<String> FORMATTER_CODES = List.of(
        "common.event.date-format",
        "datetime.pattern",
        "common.ticket-category.date-format"
    );

    @Test
    void getFormattedDates() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("alfio.i18n.public");
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ContentLanguage.ALL_LANGUAGES.forEach(cl ->
            FORMATTER_CODES.forEach(code -> Formatters.formatDateForLocale(now, code, messageSource, (a, b) -> Assertions.assertNotNull(b), cl, true))
        );
    }

    /**
     * The "locale" key drives the formatting of dates and numbers, both in the emails (format-date) and in the admin
     * UI (Intl.NumberFormat / Intl.DateTimeFormat). Every language must define its own value, otherwise it would
     * silently inherit the English one, and the value must be a BCP 47 tag ("de-DE"), which the browser accepts
     * while it rejects the Java form "de_DE".
     */
    @Test
    void everyLanguageDefinesAValidFormattingLocale() throws IOException {
        for (var language : ContentLanguage.ALL_LANGUAGES) {
            var fileName = "/alfio/i18n/public_" + language.getLanguage() + ".properties";
            if (language.equals(ContentLanguage.ENGLISH)) {
                fileName = "/alfio/i18n/public.properties";
            }
            var properties = new Properties();
            try (var stream = getClass().getResourceAsStream(fileName)) {
                assertNotNull(stream, fileName + " not found");
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            var value = properties.getProperty("locale");
            assertNotNull(value, fileName + " must define the 'locale' key");
            var locale = Locale.forLanguageTag(value.strip());
            assertEquals(value.strip(), locale.toLanguageTag(), fileName + ": 'locale' must be a language tag such as de-DE, found '" + value + "'");
            assertEquals(language.getLanguage(), locale.getLanguage(), fileName + ": 'locale' must match the language of the file");
            assertFalse(locale.getCountry().isEmpty(), fileName + ": 'locale' must include the country, e.g. de-DE");
        }
    }

    @Test
    void getFormattedLink() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("alfio.i18n.public");
        var rendered = Formatters.applyCommonMark(Map.of("en", "[link](https://alf.io)"));
        assertFalse(rendered.isEmpty());
        assertEquals(1, rendered.size());
        assertEquals("<p><a href=\"https://alf.io\" target=\"_blank\" rel=\"nofollow noopener noreferrer\">link</a></p>", rendered.get("en").trim());
    }

    @Test
    void getFormattedLinkWithAriaLabel() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("alfio.i18n.public");
        var message = messageSource.getMessage(Formatters.LINK_NEW_TAB_KEY, null, Locale.ENGLISH);
        var rendered = Formatters.applyCommonMark(Map.of("en", "[link](https://alf.io)"), messageSource);
        assertFalse(rendered.isEmpty());
        assertEquals(1, rendered.size());
        assertEquals("<p><a href=\"https://alf.io\" target=\"_blank\" rel=\"nofollow noopener noreferrer\" aria-label=\"link "+message+"\">link</a></p>", rendered.get("en").trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "https://alf.io", "tel:+41991234567", "mailto:hello@example.org"})
    void supportDifferentProtocols(String link) {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("alfio.i18n.public");
        var message = messageSource.getMessage(Formatters.LINK_NEW_TAB_KEY, null, Locale.ENGLISH);
        var rendered = Formatters.applyCommonMark(Map.of("en", "[link]("+link+")"), messageSource);
        assertFalse(rendered.isEmpty());
        assertEquals(1, rendered.size());
        if (link.startsWith("http")) {
            assertEquals("<p><a href=\""+link+"\" target=\"_blank\" rel=\"nofollow noopener noreferrer\" aria-label=\"link "+message+"\">link</a></p>", rendered.get("en").trim());
        } else {
            assertEquals("<p><a href=\""+link+"\">link</a></p>", rendered.get("en").trim());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert('pwnd')", "sms:+41993456874"})
    void unsupportedLinkMustBeCleared(String link) {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("alfio.i18n.public");
        var rendered = Formatters.applyCommonMark(Map.of("en", "[link]("+link+")"), messageSource);
        assertFalse(rendered.isEmpty());
        assertEquals(1, rendered.size());
        assertEquals("<p><a>link</a></p>", rendered.get("en").trim());
    }
}