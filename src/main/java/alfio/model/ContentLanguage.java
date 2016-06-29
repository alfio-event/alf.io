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

    public static final int ENGLISH_IDENTIFIER = 0b0010;
    public static final ContentLanguage ITALIAN = new ContentLanguage(Locale.ITALIAN, 0b0001, Locale.ITALIAN);
    public static final ContentLanguage ENGLISH = new ContentLanguage(Locale.ENGLISH, ENGLISH_IDENTIFIER, Locale.ENGLISH);
    public static final ContentLanguage GERMAN = new ContentLanguage(Locale.GERMAN,   0b0100, Locale.GERMAN);
    public static final ContentLanguage DUTCH = new ContentLanguage(new Locale("nl"), 0b1000, new Locale("nl"));

    public static final List<ContentLanguage> ALL_LANGUAGES = Arrays.asList(ITALIAN, ENGLISH, GERMAN, DUTCH);

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
        return locale.getDisplayLanguage();
    }

    public Locale getLocale() {
        return locale;
    }

    public int getValue() {
        return value;
    }

    private ContentLanguage switchDisplayLocaleTo(Locale displayLocale) {
        return new ContentLanguage(this.locale, this.value, displayLocale);
    }

    public static Function<ContentLanguage, ContentLanguage> toLanguage(Locale targetLanguage) {
        return (current) -> current.switchDisplayLocaleTo(targetLanguage);
    }
}
