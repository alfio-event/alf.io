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
import alfio.manager.system.ConfigurationManager.MaybeConfiguration;
import alfio.model.EventAndOrganizationId;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static alfio.model.system.ConfigurationKeys.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeeCalculatorTest {

    private EventAndOrganizationId event;
    private ConfigurationManager configurationManager;

    @BeforeEach
    void setUp() {
        event = mock(EventAndOrganizationId.class);
        configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getFor(eq(PLATFORM_MODE_ENABLED), any())).thenReturn(new MaybeConfiguration(PLATFORM_MODE_ENABLED, new ConfigurationKeyValuePathLevel(PLATFORM_MODE_ENABLED.name(), "true", null)));
    }

    @Test
    void calculatePercentageFee() {
        initFeesConfiguration(null, "5", null, null);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 100_00L), 5_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 500_00L), 25_00L);
    }

    @Test
    void calculatePercentageFeeWithPercentSign() {
        initFeesConfiguration(null, "5%", null, null);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 100_00L), 5_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 500_00L), 25_00L);
    }

    @Test
    void calculateFixedFee() {
        initFeesConfiguration("10", null, null, null);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 1_00L), 10_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 1_00L), 10_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 1_00L), 10_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 5_00L), 50_00L);
    }

    @Test
    void calculatePercentageAndFixedFee() {
        initFeesConfiguration("10", "5", null, null);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 100_00L), 15_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 500_00L), 75_00L);
    }

    @Test
    void applyMinimumFee() {
        initFeesConfiguration(null, "1", "0.5", null);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 10_00L), 50L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 500_00L), 5_00L);
    }

    @Test
    void applyMaximumFee() {
        initFeesConfiguration(null, "1", null, "2");
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 10_00L), 10L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(5, 500_00L), 5_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 500_00L), 2_00L);
    }

    @Test
    void testCombinations() {
        initFeesConfiguration("0.5", "3", null, "20");
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 100_00L), 3_50L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 1000_00L), 20_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(1, 834_00L), 20_00L);
        expectFeeToBe(FeeCalculator.getCalculator(event, configurationManager, "CHF").apply(2, 1680_00L), 40_00L);
    }

    private void initFeesConfiguration(String fixedFee, String percentageFee, String minimumFee, String maximumFee) {
        when(configurationManager.getFor(eq(Set.of(PLATFORM_FIXED_FEE, PLATFORM_PERCENTAGE_FEE, PLATFORM_MINIMUM_FEE, PLATFORM_MAXIMUM_FEE)), any()))
            .thenReturn(Map.of(
                PLATFORM_FIXED_FEE, new MaybeConfiguration(PLATFORM_FIXED_FEE, new ConfigurationKeyValuePathLevel(null, fixedFee, null)),
                PLATFORM_PERCENTAGE_FEE, new MaybeConfiguration(PLATFORM_PERCENTAGE_FEE, new ConfigurationKeyValuePathLevel(null, percentageFee, null)),
                PLATFORM_MINIMUM_FEE, new MaybeConfiguration(PLATFORM_MINIMUM_FEE, new ConfigurationKeyValuePathLevel(null, minimumFee, null)),
                PLATFORM_MAXIMUM_FEE, new MaybeConfiguration(PLATFORM_MAXIMUM_FEE, new ConfigurationKeyValuePathLevel(null, maximumFee, null))
            ));
    }

    private void expectFeeToBe(Optional<Long> fee, Long value) {
        assertNotNull(fee);
        assertTrue(fee.isPresent());
        assertEquals(value, fee.get());
    }
}