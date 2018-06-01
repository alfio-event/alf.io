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

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class MustacheCustomTagInterceptorTest {

    @Test
    public void translateCountryCode() {
        assertEquals("Greece", MustacheCustomTagInterceptor.translateCountryCode("GR", null));
        assertEquals("Greece", MustacheCustomTagInterceptor.translateCountryCode("EL", null));
        assertEquals("Grecia", MustacheCustomTagInterceptor.translateCountryCode("EL", Locale.ITALIAN));
    }
}