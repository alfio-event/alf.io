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
package alfio.manager.system;

import alfio.model.PurchaseContext;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationPathLevel.*;
import static org.mockito.Mockito.when;

public class ConfigurationManagerTest {

    @Test
    public void testUnionEvent() {
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> intersection = ConfigurationManager.union(SYSTEM, EVENT);
        Assertions.assertNotNull(intersection);
        List<Configuration> values = intersection.values().stream().flatMap(List::stream).collect(Collectors.toList());
        Assertions.assertTrue(ConfigurationKeys.byPathLevel(EVENT).stream().allMatch(k -> values.stream().anyMatch(v -> v.getConfigurationKey() == k && v.getConfigurationPathLevel() == EVENT)));
        Assertions.assertTrue(values.stream().anyMatch(v -> v.getConfigurationKey() == ConfigurationKeys.BASE_URL && v.getConfigurationPathLevel() == SYSTEM));
    }

    @Test
    public void testUnionOrganization() {
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> intersection = ConfigurationManager.union(EVENT, ORGANIZATION);
        Assertions.assertNotNull(intersection);
        List<Configuration> values = intersection.values().stream().flatMap(List::stream).collect(Collectors.toList());
        Assertions.assertTrue(ConfigurationKeys.byPathLevel(EVENT).stream().allMatch(k -> values.stream().anyMatch(v -> v.getConfigurationKey() == k && v.getConfigurationPathLevel() == EVENT)));
        Assertions.assertTrue(values.stream().anyMatch(v -> v.getConfigurationKey() == ConfigurationKeys.VAT_NR && v.getConfigurationPathLevel() == ORGANIZATION));
    }

}