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
import alfio.model.EventAndOrganizationId;
import alfio.model.system.Configuration;
import alfio.util.MonetaryUtil;
import org.joda.money.BigMoney;
import org.joda.money.MoneyUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.BiFunction;

import static alfio.model.system.ConfigurationKeys.*;
import static org.apache.commons.lang3.StringUtils.*;

public class FeeCalculator {
    private final BigDecimal fee;
    private final BigDecimal minimumFee;
    private final boolean percentage;
    private final int numTickets;

    private FeeCalculator(String feeAsString, String minimumFeeAsString, int numTickets) {
        this.percentage = feeAsString.endsWith("%");
        this.fee = new BigDecimal(defaultIfEmpty(substringBefore(feeAsString, "%"), "0"));
        this.minimumFee = new BigDecimal(defaultIfEmpty(trimToNull(minimumFeeAsString), "0"));
        this.numTickets = numTickets;
    }

    private BigMoney calculate(BigMoney price) {
        var result = percentage ? MonetaryUtil.calcPercentage(price, fee) : BigMoney.of(price.getCurrencyUnit(), fee);
        var minFee = BigMoney.of(price.getCurrencyUnit(), minimumFee).multipliedBy(numTickets);
        return MoneyUtils.max(result, minFee);
    }

    public static BiFunction<Integer, BigMoney, Optional<Long>> getCalculator(EventAndOrganizationId event, ConfigurationManager configurationManager) {
        return (numTickets, amount) -> {
            if(isPlatformModeEnabled(event, configurationManager)) {
                String feeAsString = configurationManager.getStringConfigValue(Configuration.from(event, PLATFORM_FEE), "0");
                String minimumFee = configurationManager.getStringConfigValue(Configuration.from(event, PLATFORM_MINIMUM_FEE), "0");
                var fee = new FeeCalculator(feeAsString, minimumFee, numTickets).calculate(amount);
                return Optional.of((long) MonetaryUtil.unitToCents(fee));
            }
            return Optional.empty();
        };
    }

    private static boolean isPlatformModeEnabled(EventAndOrganizationId event, ConfigurationManager configurationManager) {
        return configurationManager.getBooleanConfigValue(Configuration.from(event, PLATFORM_MODE_ENABLED), false);
    }
}
