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
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;

import static java.math.RoundingMode.HALF_UP;
import static java.math.RoundingMode.UP;

public final class MonetaryUtil {

    public static final BigDecimal HUNDRED = new BigDecimal("100.00");
    public static final int ROUNDING_SCALE = 10;

    private MonetaryUtil() {
    }

    public static int addVAT(String currencyCode, int priceInCents, BigDecimal vat) {
        return addVAT(centsToUnit(currencyCode, priceInCents), vat).getAmount().intValueExact();
    }

    public static BigMoney addVAT(BigMoney price, BigDecimal vat) {
        return price.plus(price.multipliedBy(vat.divide(HUNDRED, ROUNDING_SCALE, UP)));
    }

    public static BigMoney extractVAT(BigMoney price, BigDecimal vat) {
        var scaledPrice = setRoundingSafeScale(price);
        return scaledPrice.minus(scaledPrice.dividedBy(BigDecimal.ONE.add(vat.divide(HUNDRED, ROUNDING_SCALE, UP)), HALF_UP));
    }

    private static BigMoney setRoundingSafeScale(BigMoney src) {
        return src.withScale(ROUNDING_SCALE);
    }

    public static BigMoney calcPercentage(BigMoney price, BigDecimal percentage) {
        return price.multipliedBy(percentage.divide(HUNDRED, ROUNDING_SCALE, UP));
    }

    public static BigMoney calcVat(BigMoney price, BigDecimal percentage) {
        return price.multipliedBy(percentage.divide(HUNDRED, ROUNDING_SCALE, HALF_UP));
    }

    public static BigMoney centsToUnit(String currencyCode, long cents) {
        return centsToUnit(CurrencyUnit.of(currencyCode), cents);
    }

    public static BigMoney centsToUnit(CurrencyUnit currency, long cents) {
        return BigMoney.ofScale(currency, cents, currency.getDecimalPlaces());
    }

    public static BigDecimal toBigDecimal(BigMoney bigMoney) {
        return bigMoney.toMoney(HALF_UP).getAmount();
    }

    public static int unitToCents(BigMoney unit) {
        int decimalPlaces = unit.getCurrencyUnit().getDecimalPlaces();
        return unit.multipliedBy(BigDecimal.TEN.pow(decimalPlaces))
            .rounded(0, HALF_UP)
            .getAmountMajorInt();
    }

    public static int unitToCents(String currencyCode, BigDecimal unit) {
        var currencyUnit = CurrencyUnit.of(currencyCode);
        return unitToCents(Money.of(currencyUnit, unit, HALF_UP));
    }

    public static int unitToCents(Money unit) {
        return unitToCents(unit.toBigMoney());
    }

    public static String formatAmount(BigMoney amount) {
        return formatAmount(amount, true);
    }

    public static String formatAmount(BigMoney amount, boolean includeCurrencyCode) {
        var money = amount.toMoney(HALF_UP);
        if(includeCurrencyCode) {
            return money.toString();
        }
        return money.getAmount().toPlainString();
    }

    public static String formatCents(String currencyCode, long cents) {
        return toBigDecimal(centsToUnit(currencyCode, cents)).toPlainString();
    }

    public static String formatCents(String currencyCode, int cents) {
        return formatCents(currencyCode, (long) cents);
    }
}
