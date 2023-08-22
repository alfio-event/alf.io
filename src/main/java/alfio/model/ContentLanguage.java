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
package alfio.model;

import alfio.util.LocaleUtil;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class ContentLanguage {

    public static final int ENGLISH_IDENTIFIER = 0b00010;
    public static final ContentLanguage ITALIAN = new ContentLanguage(Locale.ITALIAN, 0b00001, Locale.ITALIAN);
    public static final ContentLanguage ENGLISH = new ContentLanguage(Locale.ENGLISH, ENGLISH_IDENTIFIER, Locale.ENGLISH);
    public static final ContentLanguage GERMAN = new ContentLanguage(Locale.GERMAN,   0b00100, Locale.GERMAN);
    public static final ContentLanguage DUTCH = new ContentLanguage(LocaleUtil.forLanguageTag("nl"), 0b01000, LocaleUtil.forLanguageTag("nl"));
    public static final ContentLanguage FRENCH = new ContentLanguage(Locale.FRENCH,0b10000, Locale.FRENCH);
    public static final ContentLanguage ROMANIAN = new ContentLanguage(LocaleUtil.forLanguageTag("ro"),0b100000, LocaleUtil.forLanguageTag("ro"));
    public static final ContentLanguage PORTUGUESE = new ContentLanguage(LocaleUtil.forLanguageTag("pt"),0b1000000, LocaleUtil.forLanguageTag("pt"));
    public static final ContentLanguage TURKISH = new ContentLanguage(LocaleUtil.forLanguageTag("tr"),0b10000000, LocaleUtil.forLanguageTag("tr"));
    public static final ContentLanguage SPANISH = new ContentLanguage(LocaleUtil.forLanguageTag("es"),0b100000000, LocaleUtil.forLanguageTag("es"));
    public static final ContentLanguage POLISH = new ContentLanguage(LocaleUtil.forLanguageTag("pl"),0b1000000000, LocaleUtil.forLanguageTag("pl"));
    public static final ContentLanguage DANISH = new ContentLanguage(LocaleUtil.forLanguageTag("da"),0b10000000000, LocaleUtil.forLanguageTag("da"));
    public static final ContentLanguage BULGARIAN = new ContentLanguage(LocaleUtil.forLanguageTag("bg"),0b100000000000, LocaleUtil.forLanguageTag("bg"));
    public static final ContentLanguage SWEDISH = new ContentLanguage(LocaleUtil.forLanguageTag("sv"),0b1000000000000, LocaleUtil.forLanguageTag("sv"));
    public static final ContentLanguage CZECH = new ContentLanguage(LocaleUtil.forLanguageTag("cs"),0b10000000000000, LocaleUtil.forLanguageTag("cs"));

    public static final List<ContentLanguage> ALL_LANGUAGES = List.of(ENGLISH, SPANISH, ITALIAN, GERMAN, DUTCH, FRENCH, ROMANIAN, PORTUGUESE, TURKISH, POLISH, DANISH, BULGARIAN, SWEDISH, CZECH);
    public static final int ALL_LANGUAGES_IDENTIFIER = ALL_LANGUAGES.stream().mapToInt(ContentLanguage::getValue).reduce(0, (a,b) -> a|b);

    public static List<ContentLanguage> findAllFor(int bitMask) {
        return ALL_LANGUAGES.stream()
            .filter(cl -> (cl.getValue() & bitMask) == cl.getValue())
            .collect(Collectors.toList());
    }

    private final Locale locale;
    private final int value;
    private final Locale displayLocale;

    private ContentLanguage(Locale locale, int value, Locale displayLocale) {
        this.locale = locale;
        this.value = value;
        this.displayLocale = displayLocale;
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    public String getDisplayLanguage() {
        return locale.getDisplayLanguage(displayLocale);
    }

    public Locale getLocale() {
        return locale;
    }

    public int getValue() {
        return value;
    }
}
