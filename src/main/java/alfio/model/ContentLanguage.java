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

import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@EqualsAndHashCode
public class ContentLanguage {

    public static final int ENGLISH_IDENTIFIER = 0b00010;
    public static final ContentLanguage ITALIAN = new ContentLanguage(Locale.ITALIAN, 0b00001, Locale.ITALIAN, "it");
    public static final ContentLanguage ENGLISH = new ContentLanguage(Locale.ENGLISH, ENGLISH_IDENTIFIER, Locale.ENGLISH, "gb");
    private static final ContentLanguage GERMAN = new ContentLanguage(Locale.GERMAN,   0b00100, Locale.GERMAN, "de");
    private static final ContentLanguage DUTCH = new ContentLanguage(new Locale("nl"), 0b01000, new Locale("nl"), "nl");
    private static final ContentLanguage FRENCH = new ContentLanguage(Locale.FRENCH,0b10000, Locale.FRENCH, "fr");
    private static final ContentLanguage ROMANIAN = new ContentLanguage(new Locale("ro"),0b100000, new Locale("ro"), "ro");
    private static final ContentLanguage PORTUGUESE = new ContentLanguage(new Locale("pt"),0b1000000, new Locale("pt"), "pt");

    public static final List<ContentLanguage> ALL_LANGUAGES = Arrays.asList(ITALIAN, ENGLISH, GERMAN, DUTCH, FRENCH, ROMANIAN, PORTUGUESE);
    public static final int ALL_LANGUAGES_IDENTIFIER = ALL_LANGUAGES.stream().mapToInt(ContentLanguage::getValue).reduce(0, (a,b) -> a|b);

    public static List<ContentLanguage> findAllFor(int bitMask) {
        return ALL_LANGUAGES.stream()
            .filter(cl -> (cl.getValue() & bitMask) == cl.getValue())
            .collect(Collectors.toList());
    }

    private final Locale locale;
    private final int value;
    private final Locale displayLocale;
    private final String flag;

    private ContentLanguage(Locale locale, int value, Locale displayLocale, String flag) {
        this.locale = locale;
        this.value = value;
        this.displayLocale = displayLocale;
        this.flag = flag;
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

    public String getFlag() { return flag; }

    public int getValue() {
        return value;
    }

    private ContentLanguage switchDisplayLocaleTo(Locale displayLocale) {
        return new ContentLanguage(this.locale, this.value, displayLocale, this.flag);
    }

    public static Function<ContentLanguage, ContentLanguage> toLanguage(Locale targetLanguage) {
        return (current) -> current.switchDisplayLocaleTo(targetLanguage);
    }
}
