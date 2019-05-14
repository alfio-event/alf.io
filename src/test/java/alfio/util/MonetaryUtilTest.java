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

import org.joda.money.BigMoney;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static alfio.test.util.IntegrationTestUtil.toBigMoney;
import static java.math.RoundingMode.HALF_UP;
import static org.junit.Assert.assertEquals;

public class MonetaryUtilTest {

    private static final BigMoney price = toBigMoney(10000, "CHF");

    @Test
    public void addVAT() throws Exception {
        assertEquals(toBigMoney(10750, "CHF").toMoney(), MonetaryUtil.addVAT(price, new BigDecimal("7.50")).toMoney(HALF_UP));
        assertEquals(toBigMoney(10800, "CHF").toMoney(), MonetaryUtil.addVAT(price, new BigDecimal("8.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(8000 , "CHF").toMoney(), MonetaryUtil.addVAT(toBigMoney(7407, "CHF"), new BigDecimal("8.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(400  , "CHF").toMoney(), MonetaryUtil.addVAT(toBigMoney(370, "CHF"), new BigDecimal("8.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(700  , "CHF").toMoney(), MonetaryUtil.addVAT(toBigMoney(648, "CHF"), new BigDecimal("8.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(10799, "CHF").toMoney(), MonetaryUtil.addVAT(price, new BigDecimal("7.99")).toMoney(HALF_UP));
        assertEquals(toBigMoney(10800, "CHF").toMoney(), MonetaryUtil.addVAT(price, new BigDecimal("7.999")).toMoney(HALF_UP));
        assertEquals(toBigMoney(12100, "CHF").toMoney(), MonetaryUtil.addVAT(price, new BigDecimal("21.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(12100, "BHD").toMoney(), MonetaryUtil.addVAT(toBigMoney(10000, "BHD"), new BigDecimal("21.00")).toMoney(HALF_UP));
        assertEquals(toBigMoney(12100, "LYD").toMoney(), MonetaryUtil.addVAT(toBigMoney(10000, "LYD"), new BigDecimal("21.00")).toMoney(HALF_UP));
    }

    @Test
    public void centsToUnit() throws Exception {
        assertEquals(new BigDecimal("100.00"), MonetaryUtil.centsToUnit("CHF", 10000).getAmount());
        assertEquals(new BigDecimal("100.01"), MonetaryUtil.centsToUnit("CHF", 10001).getAmount());
        assertEquals(new BigDecimal("100.99"), MonetaryUtil.centsToUnit("CHF", 10099).getAmount());
        assertEquals(new BigDecimal("97.50"), MonetaryUtil.centsToUnit("CHF", 9750).getAmount());
        assertEquals(new BigDecimal("12.100"), MonetaryUtil.centsToUnit("BHD", 12100).getAmount());
        assertEquals(new BigDecimal("9.750"), MonetaryUtil.centsToUnit("LYD", 9750).getAmount());
        assertEquals(new BigDecimal("9750"), MonetaryUtil.centsToUnit("JPY", 9750).getAmount());
    }

    @Test
    public void unitToCents() throws Exception {
        assertEquals(10000, MonetaryUtil.unitToCents("CHF", new BigDecimal("100.00")));
        assertEquals(10001, MonetaryUtil.unitToCents("CHF", new BigDecimal("100.01")));
        assertEquals(10099, MonetaryUtil.unitToCents("CHF", new BigDecimal("100.99")));
        assertEquals(10100, MonetaryUtil.unitToCents("CHF", new BigDecimal("100.999")));
        assertEquals(10100, MonetaryUtil.unitToCents("BHD", new BigDecimal("10.100")));
        assertEquals(10100, MonetaryUtil.unitToCents("LYD", new BigDecimal("10.1001")));
        assertEquals(10100, MonetaryUtil.unitToCents("JPY", new BigDecimal("10100.00")));
    }
}