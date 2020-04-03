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

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ContentLanguageTest {

    @Test
    void validateContentLanguages() {
        var sortedContentLanguages = ContentLanguage.ALL_LANGUAGES.stream()
            .sorted(Comparator.comparing(ContentLanguage::getValue))
            .collect(Collectors.toList());

        for(int i = 1; i < sortedContentLanguages.size(); i++) {
            var current = sortedContentLanguages.get(i);
            var previous = sortedContentLanguages.get(i - 1);
            assertNotEquals(current.getValue(), previous.getValue(), "error: " + previous.getDisplayLanguage() +" ("+previous.getLanguage()+")"+ " and " + current.getDisplayLanguage() +" ("+current.getLanguage()+ ") have the same value");
        }
    }

}