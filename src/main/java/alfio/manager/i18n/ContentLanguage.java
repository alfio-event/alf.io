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
package alfio.manager.i18n;

import java.util.Locale;
import java.util.function.Function;

public class ContentLanguage {

    public static final ContentLanguage ITALIAN = new ContentLanguage(Locale.ITALIAN, Locale.ENGLISH);
    public static final ContentLanguage ENGLISH = new ContentLanguage(Locale.ENGLISH, Locale.ENGLISH);

    private final Locale locale;
    private final Locale displayLocale;

    public ContentLanguage(Locale locale, Locale displayLocale) {
        this.locale = locale;
        this.displayLocale = displayLocale;
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    public String getDisplayLanguage() {
        return locale.getDisplayLanguage(displayLocale);
    }

    public ContentLanguage switchDisplayLocaleTo(Locale displayLocale) {
        return new ContentLanguage(this.locale, displayLocale);
    }

    public static Function<ContentLanguage, ContentLanguage> toLanguage(Locale targetLanguage) {
        return (current) -> current.switchDisplayLocaleTo(targetLanguage);
    }
}
