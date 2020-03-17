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

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageSourceManagerTest {

    @Test
    void cleanTranslationsForFrontend() {
        var input = Map.of("key1", "blabla{{1}}", "key2", "blabla{1}}}", "key3", "don''t stop me{{0}}", "key4", "don't stop me{{0}}");
        assertEquals(Map.of("key1", "blabla{{1}}", "key2", "blabla{{1}}", "key3", "don't stop me{{0}}", "key4", "don't stop me{{0}}"), MessageSourceManager.cleanTranslationsForFrontend(input));
    }

    @Test
    void cleanArguments() {
        assertEquals("blabla{1}", MessageSourceManager.cleanArguments("blabla{{1}}", "{$1}"));
        assertEquals("blabla{1}", MessageSourceManager.cleanArguments("blabla{{1}", "{$1}"));
        assertEquals("blabla{1}", MessageSourceManager.cleanArguments("blabla{1}}", "{$1}"));
        assertEquals("blabla{1}", MessageSourceManager.cleanArguments("blabla{1}", "{$1}"));
        assertEquals("blabla{1}", MessageSourceManager.cleanArguments("blabla{{{{{{1}}", "{$1}"));
    }
}