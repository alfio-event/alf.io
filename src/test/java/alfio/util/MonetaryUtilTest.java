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

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class MonetaryUtilTest {

    private static final int price = 10000;

    @Test
    public void addVAT() throws Exception {
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
    public void centsToUnit() throws Exception {
        assertEquals(new BigDecimal("100.00"), MonetaryUtil.centsToUnit(10000));
        assertEquals(new BigDecimal("100.01"), MonetaryUtil.centsToUnit(10001));
        assertEquals(new BigDecimal("100.99"), MonetaryUtil.centsToUnit(10099));
        assertEquals(new BigDecimal("97.50"), MonetaryUtil.centsToUnit(9750));
    }

    @Test
    public void unitToCents() throws Exception {
        assertEquals(10000, MonetaryUtil.unitToCents(new BigDecimal("100.00")));
        assertEquals(10001, MonetaryUtil.unitToCents(new BigDecimal("100.01")));
        assertEquals(10099, MonetaryUtil.unitToCents(new BigDecimal("100.99")));
        assertEquals(10100, MonetaryUtil.unitToCents(new BigDecimal("100.999")));
    }
}