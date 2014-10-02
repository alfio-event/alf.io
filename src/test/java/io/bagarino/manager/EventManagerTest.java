/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager;

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class EventManagerTest {{

        final int hundred = 10000;//100.00
        describe("EventManager", it -> {
            it.should("deduct vat if included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, true, false)).is(9091));
            it.should("not deduct vat if not included into event price", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, false)).is(hundred));
            it.should("return BigDecimal.ZERO if the event is free of charge", expect -> expect.that(EventManager.evaluatePrice(hundred, BigDecimal.TEN, false, true)).is(0));
        });
}}