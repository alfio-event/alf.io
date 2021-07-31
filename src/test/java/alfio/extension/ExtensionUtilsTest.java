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
package alfio.extension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionUtilsTest {

    private Scriptable scope;

    @BeforeEach
    void init() {
        scope = Context.enter().initSafeStandardObjects(null, true);
    }

    @AfterEach
    void cleanup() {
        Context.exit();
    }

    @Test
    void convertIntegerToJson() {
        var db = 1.0D;
        assertEquals("{\"number\":1}", ExtensionUtils.convertToJson(Context.javaToJS(Map.of("number", db), scope)));
    }

    @Test
    void convertDecimalToJson() {
        var db = 1.1D;
        assertEquals("{\"number\":1.1}", ExtensionUtils.convertToJson(Context.javaToJS(Map.of("number", db), scope)));
    }
}