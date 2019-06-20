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
package alfio.util;

import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.Ticket;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Optional;

public final class LocaleUtil {
    private LocaleUtil() {}

    public static Locale getTicketLanguage(Ticket t, Locale fallbackLocale) {
        return Optional.ofNullable(t.getUserLanguage())
                .filter(StringUtils::isNotBlank)
                .map(Locale::forLanguageTag)
                .orElse(fallbackLocale);
    }

    public static Locale forLanguageTag(String lang) {
        if (lang == null) {
           return Locale.ENGLISH;
        } else {
            return Locale.forLanguageTag(lang);
        }
    }

    public static Locale forLanguageTag(String lang, Event event) {
        String cleanedUpLang = StringUtils.trimToEmpty(lang).toLowerCase(Locale.ENGLISH);
        var filteredLang = event.getContentLanguages()
            .stream()
            .filter(l -> cleanedUpLang.equalsIgnoreCase(l.getLanguage()))
            .findFirst()
            .map(ContentLanguage::getLanguage)
            //vvv fallback
            .orElseGet(() -> event.getContentLanguages().stream().findFirst().map(ContentLanguage::getLanguage).orElse("en"));
        return forLanguageTag(filteredLang);
    }
}
