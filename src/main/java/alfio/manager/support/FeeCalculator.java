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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.EventAndOrganizationId;
import alfio.util.MonetaryUtil;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import static alfio.model.system.ConfigurationKeys.*;
import static org.apache.commons.lang3.StringUtils.*;

public class FeeCalculator {
    private final BigDecimal fee;
    private final BigDecimal minimumFee;
    private final boolean percentage;
    private final int numTickets;
    private final String currencyCode;

    private FeeCalculator(String feeAsString, String minimumFeeAsString, String currencyCode, int numTickets) {
        this.percentage = feeAsString.endsWith("%");
        this.fee = new BigDecimal(defaultIfEmpty(substringBefore(feeAsString, "%"), "0"));
        this.minimumFee = new BigDecimal(defaultIfEmpty(trimToNull(minimumFeeAsString), "0"));
        this.numTickets = numTickets;
        this.currencyCode = currencyCode;
    }

    private long calculate(long price) {
        long result = percentage ? MonetaryUtil.calcPercentage(price, fee, BigDecimal::longValueExact) : MonetaryUtil.unitToCents(fee, currencyCode);
        long minFee = MonetaryUtil.unitToCents(minimumFee, currencyCode, BigDecimal::longValueExact) * numTickets;
        return Math.max(result, minFee);
    }

    public static BiFunction<Integer, Long, Optional<Long>> getCalculator(EventAndOrganizationId event, ConfigurationManager configurationManager, String currencyCode) {
        return (numTickets, amountInCent) -> {
            if(isPlatformModeEnabled(event, configurationManager)) {
                var fees = configurationManager.getFor(Set.of(PLATFORM_FEE, PLATFORM_MINIMUM_FEE), ConfigurationLevel.event(event));
                String feeAsString = fees.get(PLATFORM_FEE).getValueOrDefault("0");
                String minimumFee = fees.get(PLATFORM_MINIMUM_FEE).getValueOrDefault("0");
                return Optional.of(new FeeCalculator(feeAsString, minimumFee, currencyCode, numTickets).calculate(amountInCent));
            }
            return Optional.empty();
        };
    }

    private static boolean isPlatformModeEnabled(EventAndOrganizationId event, ConfigurationManager configurationManager) {
        return configurationManager.getFor(PLATFORM_MODE_ENABLED, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault(false);
    }
}
