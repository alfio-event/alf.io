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
package alfiov2.builder;

import alfio.model.ContentLanguage;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.Set;

public class I18nTextBuilder {


    public static class LocalizedText {

    }

    @RequiredArgsConstructor
    public static class TitleDescriptionPair {
        private final String title;
        private final String description;
        private final ContentLanguage language;
    }

    public static Set<LocalizedText> text(ContentLanguage lang, String text) {
        return Collections.singleton(new LocalizedText());
    }

    public static TitleDescriptionPair text(ContentLanguage lang, String title, String description) {
        return new TitleDescriptionPair(title, description, lang);
    }
}
