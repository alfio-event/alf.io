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
package alfio.controller.decorator;

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class DecoratorUtilTest {{
    describe("SaleableTicketCategory.getAmountOfTickets", it -> {

        it.should("return a range from 0 to 5", expect -> expect.that(DecoratorUtil.generateRangeOfTicketQuantity(5,5)).is(new int[]{0,1,2,3,4,5}));
        it.should("return a range from 0 to 1", expect -> expect.that(DecoratorUtil.generateRangeOfTicketQuantity(1, 50)).is(new int[]{0,1}));
        it.should("return a range from 0 to 0", expect -> expect.that(DecoratorUtil.generateRangeOfTicketQuantity(-1, 50)).is(new int[] {0}));
    });
}}