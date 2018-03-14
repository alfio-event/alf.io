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
package alfio.manager.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static alfio.manager.system.DataMigrator.parseVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("DataMigrator: parse version")
public class DataMigratorTest {

    private final BigDecimal target = new BigDecimal("1.5");

    @Test
    @DisplayName("parse a stable version")
    void parseStable() {
        assertEquals(target, parseVersion("1.5"));
    }

    @Test
    @DisplayName("parse a snapshot version")
    void parseSnapshot() {
        assertEquals(target, parseVersion("1.5-SNAPSHOT"));
    }

    @Test
    @DisplayName("parse a patch release")
    void parsePatch() {
        assertEquals(new BigDecimal("1.51"), parseVersion("1.5.1"));
    }

    @Test
    @DisplayName("return zero if unknown")
    void zeroIfUnknown() {
        assertEquals(BigDecimal.ZERO, parseVersion("NOT_VALID"));
    }
}