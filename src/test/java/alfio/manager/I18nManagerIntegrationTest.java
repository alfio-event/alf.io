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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.manager.i18n.I18nManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.model.ContentLanguage;
import alfio.util.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class I18nManagerIntegrationTest extends BaseIntegrationTest {

    private static final ZonedDateTime DATE = ZonedDateTime.of(1999, 2, 3, 4, 5, 6, 7, ZoneId.of("Europe/Zurich"));


    @Autowired
    private MessageSourceManager messageSourceManager;

    @Autowired
    private I18nManager i18nManager;


    /**
     * Check for all the patterns
     */
    @Test
    public void testDateFormattingCorrectness() {

        for (ContentLanguage cl : i18nManager.getAvailableLanguages()) {
            formatDateWith(cl); //<- will launch an exception if the format is not valid
            assertTrue(true);
        }
    }

    /**
     * TODO: check what are the standard date formatting for each "country". Note: at some point we will allow to override
     *       the keys, so we can imagine that we can override automatically given the country (but let the user override).
     */
    @Test
    public void testFormatSpecifically() {
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ITALIAN));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ENGLISH));
        assertEquals("03.02.1999 04:05", formatDateWith(ContentLanguage.GERMAN));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.DUTCH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.FRENCH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ROMANIAN));
        assertEquals("03.02.1999 04:05", formatDateWith(ContentLanguage.PORTUGUESE));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.TURKISH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.SPANISH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.POLISH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.DANISH));
        assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.BULGARIAN));
    }

    private String formatDateWith(ContentLanguage cl) {
        var messageSource = messageSourceManager.getRootMessageSource();
        String pattern = messageSource.getMessage("datetime.pattern", null, cl.getLocale());
        return DateTimeFormatter.ofPattern(pattern, cl.getLocale()).format(DATE);
    }
}
