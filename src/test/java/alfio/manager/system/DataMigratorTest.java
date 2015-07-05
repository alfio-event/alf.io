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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static alfio.manager.system.DataMigrator.parseVersion;
import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class DataMigratorTest {{
    describe("parseVersion", it -> {
        BigDecimal target = new BigDecimal("1.5");
        it.should("parse a stable version", expect -> expect.that(parseVersion("1.5")).is(target));
        it.should("parse a snapshot version", expect -> expect.that(parseVersion("1.5-SNAPSHOT")).is(target));
        it.should("parse a patch release", expect -> expect.that(parseVersion("1.5.1")).is(new BigDecimal("1.51")));
        it.should("return zero if unknown", expect -> expect.that(parseVersion("NOT_VALID")).is(BigDecimal.ZERO));
    });
}}