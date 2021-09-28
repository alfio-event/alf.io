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

import org.apache.commons.lang3.StringUtils;
import org.joda.money.CurrencyUnit;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import static java.math.RoundingMode.*;

public final class MonetaryUtil {

    public static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final int ROUNDING_SCALE = 10;

    private MonetaryUtil() {
    }

    public static int addVAT(int priceInCents, BigDecimal vat) {
        return addVAT(new BigDecimal(priceInCents), vat).intValueExact();
    }

    private static BigDecimal addVAT(BigDecimal price, BigDecimal vat) {
        return price.add(price.multiply(vat.divide(HUNDRED, ROUNDING_SCALE, UP))).setScale(0, HALF_UP);
    }

    public static BigDecimal extractVAT(BigDecimal price, BigDecimal vat) {
        return price.subtract(price.divide(BigDecimal.ONE.add(vat.divide(HUNDRED, ROUNDING_SCALE, UP)), ROUNDING_SCALE, HALF_DOWN));
    }

    public static <T extends Number> T calcPercentage(long priceInCents, BigDecimal vat, Function<BigDecimal, T> converter) {
        BigDecimal result = new BigDecimal(priceInCents).multiply(vat.divide(HUNDRED, ROUNDING_SCALE, UP))
            .setScale(0, HALF_UP);
        return converter.apply(result);
    }

    public static BigDecimal calcVat(BigDecimal price, BigDecimal percentage) {
        return price.multiply(percentage.divide(HUNDRED, ROUNDING_SCALE, HALF_UP));
    }

    public static BigDecimal centsToUnit(int cents, String currencyCode) {
        return centsToUnit((long) cents, currencyCode);
    }

    public static BigDecimal centsToUnit(long cents, String currencyCode) {
        return centsToUnit(cents, currencyCode, false);
    }

    public static BigDecimal centsToUnit(long cents, String currencyCode, boolean formatZero) {
        if((cents == 0 && !formatZero) || StringUtils.isEmpty(currencyCode)) {
            return BigDecimal.ZERO;
        }
        var currencyUnit = CurrencyUnit.of(currencyCode.toUpperCase(Locale.ENGLISH));
        int scale = currencyUnit.getDecimalPlaces();
        return new BigDecimal(cents).divide(BigDecimal.TEN.pow(scale), scale, HALF_UP);
    }

    public static BigDecimal fixScale(BigDecimal raw, String currencyCode) {
        var currencyUnit = CurrencyUnit.of(currencyCode.toUpperCase(Locale.ENGLISH));
        return raw.setScale(currencyUnit.getDecimalPlaces(), HALF_UP);
    }

    public static int unitToCents(BigDecimal unit, String currencyCode) {
        if (StringUtils.isEmpty(currencyCode)) {
            return 0;
        }
        return unitToCents(unit, currencyCode, BigDecimal::intValueExact);
    }

    public static <T extends Number> T unitToCents(BigDecimal unit, String currencyCode, Function<BigDecimal, T> converter) {
        int scale = StringUtils.isEmpty(currencyCode) ? 2 : CurrencyUnit.of(currencyCode.toUpperCase(Locale.ENGLISH)).getDecimalPlaces();
        BigDecimal result = unit.multiply(BigDecimal.TEN.pow(scale)).setScale(0, HALF_UP);
        return converter.apply(result);
    }

    public static String formatCents(int cents, String currencyCode) {
        return formatCents((long) cents, currencyCode);
    }

    public static String formatCents(long cents, String currencyCode, boolean formatZero) {
        if (StringUtils.isEmpty(currencyCode)) {
            return "0";
        }
        return centsToUnit(cents, currencyCode, formatZero).toPlainString();
    }

    public static String formatCents(long cents, String currencyCode) {
        return formatCents(cents, currencyCode, false);
    }

    public static String formatUnit(BigDecimal unit, String currencyCode) {
        if (StringUtils.isEmpty(currencyCode)) {
            return "0";
        }

        var currencyUnit = CurrencyUnit.of(Objects.requireNonNull(currencyCode).toUpperCase(Locale.ENGLISH));
        return Objects.requireNonNull(unit).setScale(currencyUnit.getDecimalPlaces(), HALF_UP).toPlainString();
    }
}
