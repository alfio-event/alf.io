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

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonetaryUtilTest {

    private static final int price = 10000;

    @Test
    void addVAT() {
        assertEquals(10750, MonetaryUtil.addVAT(price, new BigDecimal("7.50")));
        assertEquals(10800, MonetaryUtil.addVAT(price, new BigDecimal("8.00")));
        assertEquals(8000, MonetaryUtil.addVAT(7407, new BigDecimal("8.00")));
        assertEquals(400, MonetaryUtil.addVAT(370, new BigDecimal("8.00")));
        assertEquals(700, MonetaryUtil.addVAT(648, new BigDecimal("8.00")));
        assertEquals(10799, MonetaryUtil.addVAT(price, new BigDecimal("7.99")));
        assertEquals(10800, MonetaryUtil.addVAT(price, new BigDecimal("7.999")));
        assertEquals(12100, MonetaryUtil.addVAT(price, new BigDecimal("21.00")));
    }

    @Test
    void centsToUnit() {
        assertEquals(new BigDecimal("100.00"), MonetaryUtil.centsToUnit(10000, "CHF"));
        assertEquals(new BigDecimal("100.01"), MonetaryUtil.centsToUnit(10001, "CHF"));
        assertEquals(new BigDecimal("100.99"), MonetaryUtil.centsToUnit(10099, "CHF"));
        assertEquals(new BigDecimal("97.50"), MonetaryUtil.centsToUnit(9750, "CHF"));

        assertEquals(new BigDecimal("101"), MonetaryUtil.centsToUnit(101, "JPY"));
        assertEquals(new BigDecimal("101.000"), MonetaryUtil.centsToUnit(101000, "BHD"));
    }

    @Test
    void unitToCents() {
        assertEquals(10000, MonetaryUtil.unitToCents(new BigDecimal("100.00"), "CHF"));
        assertEquals(10001, MonetaryUtil.unitToCents(new BigDecimal("100.01"), "CHF"));
        assertEquals(10001, MonetaryUtil.unitToCents(new BigDecimal("100.01"), "chf"));
        assertEquals(10099, MonetaryUtil.unitToCents(new BigDecimal("100.99"), "CHF"));
        assertEquals(10099, MonetaryUtil.unitToCents(new BigDecimal("100.99"), "chf"));
        assertEquals(10100, MonetaryUtil.unitToCents(new BigDecimal("100.999"), "CHF"));
        assertEquals(10100, MonetaryUtil.unitToCents(new BigDecimal("100.999"), "chf"));

        assertEquals(101, MonetaryUtil.unitToCents(new BigDecimal("101"), "JPY"));
        assertEquals(101, MonetaryUtil.unitToCents(new BigDecimal("101"), "jpy"));
        assertEquals(101000, MonetaryUtil.unitToCents(new BigDecimal("101.000"), "BHD"));
        assertEquals(101000, MonetaryUtil.unitToCents(new BigDecimal("101.000"), "bhd"));
    }

    @Test
    void formatCents() {
        assertEquals("1000", MonetaryUtil.formatCents(1000, "JPY"));
        assertEquals("1000", MonetaryUtil.formatCents(1000, "jpy"));
        assertEquals("10.00", MonetaryUtil.formatCents(1000, "EUR"));
        assertEquals("10.00", MonetaryUtil.formatCents(1000, "eur"));
        assertEquals("1.000", MonetaryUtil.formatCents(1000, "BHD"));
    }

    @Test
    void formatUnit() {
        assertEquals("1000.00", MonetaryUtil.formatUnit(new BigDecimal("1000.00"), "CHF"));
        assertEquals("1000.00", MonetaryUtil.formatUnit(new BigDecimal("1000.00"), "chf"));
        assertEquals("1000", MonetaryUtil.formatUnit(new BigDecimal("1000.00"), "JPY"));
        assertEquals("1000", MonetaryUtil.formatUnit(new BigDecimal("1000.00"), "jpy"));
        assertEquals("1000.000", MonetaryUtil.formatUnit(new BigDecimal("1000.000"), "BHD"));
        assertEquals("1000.000", MonetaryUtil.formatUnit(new BigDecimal("1000.000"), "bhd"));
    }
}