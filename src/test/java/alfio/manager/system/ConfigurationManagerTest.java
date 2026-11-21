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

import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationPathLevel.*;

public class ConfigurationManagerTest {

    @Test
    public void testUnionEvent() {
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> intersection = ConfigurationManager.union(SYSTEM, PURCHASE_CONTEXT);
        Assertions.assertNotNull(intersection);
        List<Configuration> values = intersection.values().stream().flatMap(List::stream).collect(Collectors.toList());
        Assertions.assertTrue(ConfigurationKeys.byPathLevel(PURCHASE_CONTEXT).stream().allMatch(k -> values.stream().anyMatch(v -> v.getConfigurationKey() == k && v.getConfigurationPathLevel() == PURCHASE_CONTEXT)));
        Assertions.assertTrue(values.stream().anyMatch(v -> v.getConfigurationKey() == ConfigurationKeys.BASE_URL && v.getConfigurationPathLevel() == SYSTEM));
    }

    @Test
    public void testUnionOrganization() {
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> intersection = ConfigurationManager.union(PURCHASE_CONTEXT, ORGANIZATION);
        Assertions.assertNotNull(intersection);
        List<Configuration> values = intersection.values().stream().flatMap(List::stream).collect(Collectors.toList());
        Assertions.assertTrue(ConfigurationKeys.byPathLevel(PURCHASE_CONTEXT).stream().allMatch(k -> values.stream().anyMatch(v -> v.getConfigurationKey() == k && v.getConfigurationPathLevel() == PURCHASE_CONTEXT)));
        Assertions.assertTrue(values.stream().anyMatch(v -> v.getConfigurationKey() == ConfigurationKeys.ENABLE_REVERSE_CHARGE_ONLINE && v.getConfigurationPathLevel() == ORGANIZATION));
    }

    @Test
    void systemSettingsShownToOwnersAreYesNoSettingsAnOrganizationOrEventCanSet() {
        for (var key : ConfigurationManager.SYSTEM_SETTINGS_SHOWN_TO_OWNERS) {
            Assertions.assertTrue(key.isBooleanComponentType(), key + " is not a yes/no setting");
            Assertions.assertFalse(key.isInternal(), key + " is internal");
            Assertions.assertTrue(key.supports(SYSTEM), key + " can't be set by the system");
            Assertions.assertTrue(key.supports(ORGANIZATION) || key.supports(PURCHASE_CONTEXT),
                key + " can't be set by an organization or an event");
        }
    }

    @Test
    void ownersSeeTheSystemValueOfTheAllowedSettingsOnly() {
        var systemValues = Map.of(
            ConfigurationKeys.ON_SITE_ENABLED, "true",
            ConfigurationKeys.PAYPAL_LIVE_MODE, "true",
            ConfigurationKeys.BASE_URL, "https://example.org");

        var result = ConfigurationManager.systemSettingsShownToOwners(systemValues);

        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(true, "true"), result.get("ON_SITE_ENABLED"));
        // not set by the system: the default applies
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(false, "false"), result.get("BANK_TRANSFER_ENABLED"));
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(false, "true"), result.get("ENABLE_TICKET_TRANSFER"));
        // not in the allow-list
        Assertions.assertFalse(result.containsKey("PAYPAL_LIVE_MODE"));
        Assertions.assertFalse(result.containsKey("BASE_URL"));
        Assertions.assertEquals(ConfigurationManager.SYSTEM_SETTINGS_SHOWN_TO_OWNERS.size(), result.size());
    }

    @Test
    void atEventLevelOwnersSeeTheOrganizationValueThenTheAllowedSystemValue() {
        var organizationValues = Map.of(
            ConfigurationKeys.ENABLE_WAITING_QUEUE, "false",
            ConfigurationKeys.BANK_ACCOUNT_NR, "CH00 0000");
        var systemValues = Map.of(
            ConfigurationKeys.ENABLE_WAITING_QUEUE, "true",
            ConfigurationKeys.ENABLE_HTML_EMAILS, "false",
            ConfigurationKeys.PAYPAL_LIVE_MODE, "true",
            ConfigurationKeys.BASE_URL, "https://example.org");

        var result = ConfigurationManager.inheritedSettingsForOwnerAtEvent(organizationValues, systemValues);

        // the organization wins
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(true, "false"), result.get("ENABLE_WAITING_QUEUE"));
        // a text set by the organization: only that a value is set
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(true, null), result.get("BANK_ACCOUNT_NR"));
        // not set by the organization: the system value, or the default
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(true, "false"), result.get("ENABLE_HTML_EMAILS"));
        Assertions.assertEquals(new ConfigurationManager.InheritedSetting(false, "true"), result.get("SEND_TICKETS_AUTOMATICALLY"));
        // never from the system
        Assertions.assertFalse(result.containsKey("PAYPAL_LIVE_MODE"));
        Assertions.assertFalse(result.containsKey("BASE_URL"));
    }

}