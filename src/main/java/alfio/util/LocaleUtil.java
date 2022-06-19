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
import alfio.model.LocalizedContent;
import alfio.model.Ticket;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public static Locale forLanguageTag(String lang, LocalizedContent localizedContent) {
        String cleanedUpLang = StringUtils.trimToEmpty(lang).toLowerCase(Locale.ENGLISH);
        var filteredLang = localizedContent.getContentLanguages()
            .stream()
            .filter(l -> cleanedUpLang.equalsIgnoreCase(l.getLanguage()))
            .findFirst()
            .map(ContentLanguage::getLanguage)
            //vvv fallback
            .orElseGet(() -> localizedContent.getContentLanguages().stream().findFirst().map(ContentLanguage::getLanguage).orElse("en"));
        return forLanguageTag(filteredLang);
    }

    public static ZonedDateTime atZone(ZonedDateTime in, ZoneId zone) {
        if(in != null) {
            return in.withZoneSameInstant(zone);
        }
        return null;
    }

    public static ZonedDateTime atZone(LocalDateTime in, ZoneId zone) {
        if(in != null) {
            return in.atZone(Objects.requireNonNull(zone));
        }
        return null;
    }

    public static Map<String, String> formatDate(ZonedDateTime date, Map<Locale, String> datePatterns) {
        if(date == null) {
            return null;
        }
        return datePatterns.entrySet().stream()
            .map(dp -> Map.entry(dp.getKey().getLanguage(), DateTimeFormatter.ofPattern(dp.getValue()).format(date)))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
