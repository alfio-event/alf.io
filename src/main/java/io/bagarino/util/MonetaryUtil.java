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
package io.bagarino.util;

import java.math.BigDecimal;

import static java.math.RoundingMode.HALF_UP;

public final class MonetaryUtil {

    public static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private MonetaryUtil() {
    }

    public static int addVAT(int priceInCents, BigDecimal vat) {
        BigDecimal price = new BigDecimal(priceInCents);
        return price.add(price.multiply(vat.divide(HUNDRED))).setScale(0, HALF_UP).intValueExact();
    }

    public static int removeVAT(int priceInCents, BigDecimal vat) {
        return new BigDecimal(priceInCents)
                .divide(BigDecimal.ONE.add(vat.divide(HUNDRED)), 5, HALF_UP)
                .setScale(0, HALF_UP)
                .intValueExact();
    }

    public static BigDecimal centsToUnit(int cents) {
        return new BigDecimal(cents).divide(HUNDRED, 2, HALF_UP);
    }

    public static int unitToCents(BigDecimal unit) {
        return unit.multiply(HUNDRED).setScale(0, HALF_UP).intValueExact();
    }

    public static String formatCents(int cents) {
        return centsToUnit(cents).toPlainString();
    }
}
