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

import java.math.BigDecimal;

import static java.math.RoundingMode.*;

public final class MonetaryUtil {

    public static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private MonetaryUtil() {
    }

    public static int addVAT(int priceInCents, BigDecimal vat) {
        return addVAT(new BigDecimal(priceInCents), vat).intValueExact();
    }

    public static BigDecimal addVAT(BigDecimal price, BigDecimal vat) {
        return price.add(price.multiply(vat.divide(HUNDRED, 5, UP))).setScale(0, HALF_UP);
    }

    public static BigDecimal extractVAT(BigDecimal price, BigDecimal vat) {
        return price.subtract(price.divide(BigDecimal.ONE.add(vat.divide(HUNDRED, 5, UP)), 5, HALF_DOWN));
    }

    public static int calcPercentage(int priceInCents, BigDecimal vat) {
        return new BigDecimal(priceInCents).multiply(vat.divide(HUNDRED, 5, UP))
                .setScale(0, HALF_UP)
                .intValueExact();
    }

    public static BigDecimal calcVat(BigDecimal price, BigDecimal percentage) {
        return price.multiply(percentage.divide(HUNDRED, 5, HALF_UP));
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
