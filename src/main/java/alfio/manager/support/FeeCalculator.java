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
package alfio.manager.support;

import alfio.manager.system.ConfigurationManager;
import alfio.model.Configurable;
import alfio.util.MonetaryUtil;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static alfio.model.system.ConfigurationKeys.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.commons.lang3.StringUtils.*;

public class FeeCalculator {
    private final BigDecimal fee;
    private final BigDecimal percentageFee;
    private final BigDecimal minimumFee;
    private final BigDecimal maximumFee;
    private final boolean maxFeeDefined;
    private final int numTickets;
    private final String currencyCode;

    private FeeCalculator(String feeAsString, String percentageFeeAsString, String minimumFeeAsString, String maxFeeAsString, String currencyCode, int numTickets) {
        this.fee = new BigDecimal(defaultIfEmpty(trimToNull(feeAsString), "0"));
        this.percentageFee = new BigDecimal(defaultIfEmpty(substringBefore(trimToNull(percentageFeeAsString), "%"), "0"));
        this.minimumFee = new BigDecimal(defaultIfEmpty(trimToNull(minimumFeeAsString), "0"));
        this.maximumFee = isEmpty(maxFeeAsString) ? null : new BigDecimal(trimToNull(maxFeeAsString));
        this.maxFeeDefined = this.maximumFee != null;
        this.numTickets = numTickets;
        this.currencyCode = currencyCode;
    }

    private long calculate(long price) {
        long percentage = MonetaryUtil.calcPercentage(price, percentageFee, BigDecimal::longValueExact);
        long fixed = (long) MonetaryUtil.unitToCents(fee, currencyCode) * numTickets;
        long minFee = MonetaryUtil.unitToCents(minimumFee, currencyCode, BigDecimal::longValueExact) * numTickets;
        long maxFee = maxFeeDefined ? MonetaryUtil.unitToCents(maximumFee, currencyCode, BigDecimal::longValueExact) * numTickets : Long.MAX_VALUE;
        return min(maxFee, max(percentage + fixed, minFee));
    }

    public static BiFunction<Integer, Long, Optional<Long>> getCalculator(Configurable configurable, ConfigurationManager configurationManager, String currencyCode) {
        return (numTickets, amountInCent) -> {
            if(isPlatformModeEnabled(configurable, configurationManager)) {
                var fees = configurationManager.getFor(Set.of(PLATFORM_FIXED_FEE, PLATFORM_PERCENTAGE_FEE, PLATFORM_MINIMUM_FEE, PLATFORM_MAXIMUM_FEE), configurable.getConfigurationLevel());
                String fixedFee = fees.get(PLATFORM_FIXED_FEE).getValueOrDefault("0");
                String percentageFee = fees.get(PLATFORM_PERCENTAGE_FEE).getValueOrDefault("0");
                String minimumFee = fees.get(PLATFORM_MINIMUM_FEE).getValueOrDefault("0");
                String maximumFee = fees.get(PLATFORM_MAXIMUM_FEE).getValueOrDefault("");
                return Optional.of(new FeeCalculator(fixedFee, percentageFee, minimumFee, maximumFee, currencyCode, numTickets).calculate(amountInCent));
            }
            return Optional.empty();
        };
    }

    private static boolean isPlatformModeEnabled(Configurable configurable, ConfigurationManager configurationManager) {
        return configurationManager.getFor(PLATFORM_MODE_ENABLED, configurable.getConfigurationLevel()).getValueAsBooleanOrDefault();
    }
}
