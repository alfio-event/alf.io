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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.junit.runner.RunWith;

import java.math.BigDecimal;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class MonetaryUtilTest {{
        int price = 10000;
        describe("MonetaryUtil.addVAT", it -> {
            it.should("include 7.5% of VAT", expect -> expect.that(MonetaryUtil.addVAT(price, new BigDecimal("7.50"))).is(10750));
            it.should("include 8% of VAT", expect -> expect.that(MonetaryUtil.addVAT(price, new BigDecimal("8.00"))).is(10800));
            it.should("include 8% of VAT (corner case)", expect -> expect.that(MonetaryUtil.addVAT(7407, new BigDecimal("8.00"))).is(8000));
            it.should("include 7.99% of VAT", expect -> expect.that(MonetaryUtil.addVAT(price, new BigDecimal("7.99"))).is(10799));
            it.should("include 7.999% of VAT", expect -> expect.that(MonetaryUtil.addVAT(price, new BigDecimal("7.999"))).is(10800));
            it.should("include 21% of VAT", expect -> expect.that(MonetaryUtil.addVAT(price, new BigDecimal("21.00"))).is(12100));
        });

        describe("MonetaryUtil.removeVAT", it -> {
            it.should("remove 7.5% of VAT", expect -> expect.that(MonetaryUtil.removeVAT(10750, new BigDecimal("7.50"))).is(price));
            it.should("remove 8% of VAT", expect -> expect.that(MonetaryUtil.removeVAT(10800, new BigDecimal("8.00"))).is(price));
            it.should("remove 8% of VAT (corner case)", expect -> expect.that(MonetaryUtil.removeVAT(8000, new BigDecimal("8.00"))).is(7407));
            it.should("remove 7.99% of VAT", expect -> expect.that(MonetaryUtil.removeVAT(10799, new BigDecimal("7.99"))).is(price));
            it.should("remove 7.999% of VAT", expect -> expect.that(MonetaryUtil.removeVAT(10800, new BigDecimal("7.999"))).is(price));
            it.should("remove 21% of VAT", expect -> expect.that(MonetaryUtil.removeVAT(12100, new BigDecimal("21.00"))).is(price));
        });

        describe("MonetaryUtil.centsToUnit", it -> {
            it.should("convert 10000", expect -> expect.that(MonetaryUtil.centsToUnit(10000)).is(new BigDecimal("100.00")));
            it.should("convert 10001", expect -> expect.that(MonetaryUtil.centsToUnit(10001)).is(new BigDecimal("100.01")));
            it.should("convert 10099", expect -> expect.that(MonetaryUtil.centsToUnit(10099)).is(new BigDecimal("100.99")));
            it.should("convert 9750", expect -> expect.that(MonetaryUtil.centsToUnit(9750)).is(new BigDecimal("97.50")));
        });

        describe("MonetaryUtil.unitToCents", it -> {
            it.should("convert 100.00", expect -> expect.that(MonetaryUtil.unitToCents(new BigDecimal("100.00"))).is(10000));
            it.should("convert 100.01", expect -> expect.that(MonetaryUtil.unitToCents(new BigDecimal("100.01"))).is(10001));
            it.should("convert 100.99", expect -> expect.that(MonetaryUtil.unitToCents(new BigDecimal("100.99"))).is(10099));
            it.should("convert 100.999", expect -> expect.that(MonetaryUtil.unitToCents(new BigDecimal("100.999"))).is(10100));
        });
}}