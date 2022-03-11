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
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
}