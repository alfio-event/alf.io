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
import alfio.model.ContentLanguage;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class I18nManagerIntegrationTest {

    private static final ZonedDateTime DATE = ZonedDateTime.of(1999, 2, 3, 4, 5, 6, 7, ZoneId.of("Europe/Zurich"));


    @Autowired
    private MessageSource messageSource;

    @Autowired
    private I18nManager i18nManager;


    /**
     * Check for all the patterns
     */
    @Test
    public void testDateFormattingCorrectness() {

        for (ContentLanguage cl : i18nManager.getAvailableLanguages()) {
            formatDateWith(cl); //<- will launch an exception if the format is not valid
        }
    }

    /**
     * TODO: check what are the standard date formatting for each "country". Note: at some point we will allow to override
     *       the keys, so we can imagine that we can override automatically given the country (but let the user override).
     */
    @Test
    public void testFormatSpecifically() {
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ITALIAN));
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ENGLISH));
        Assert.assertEquals("03.02.1999 04:05", formatDateWith(ContentLanguage.GERMAN));
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.DUTCH));
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.FRENCH));
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.ROMANIAN));
        Assert.assertEquals("03.02.1999 04:05", formatDateWith(ContentLanguage.PORTUGUESE));
        Assert.assertEquals("03/02/1999 04:05", formatDateWith(ContentLanguage.TURKISH));
    }

    private String formatDateWith(ContentLanguage cl) {
        String pattern = messageSource.getMessage("datetime.pattern", null, cl.getLocale());
        return DateTimeFormatter.ofPattern(pattern, cl.getLocale()).format(DATE);
    }
}
