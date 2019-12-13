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
package alfio.manager.testSupport;

import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;

import java.util.Map;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.model.system.ConfigurationKeys.STRIPE_CONNECTED_ID;

public abstract class StripeUtils {

    public static Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> completeStripeConfiguration(boolean enableSCA) {
        return Map.of(
            STRIPE_ENABLE_SCA, maybeConfiguration(STRIPE_ENABLE_SCA, String.valueOf(enableSCA), ConfigurationPathLevel.SYSTEM),
            BASE_URL, maybeConfiguration(BASE_URL, "xxx", ConfigurationPathLevel.SYSTEM),
            STRIPE_WEBHOOK_PAYMENT_KEY, maybeConfiguration(BASE_URL, "xxx", ConfigurationPathLevel.SYSTEM),
            STRIPE_CC_ENABLED, maybeConfiguration(STRIPE_CC_ENABLED, "true", ConfigurationPathLevel.SYSTEM),
            PLATFORM_MODE_ENABLED, maybeConfiguration(PLATFORM_MODE_ENABLED, "true", ConfigurationPathLevel.SYSTEM),
            STRIPE_CONNECTED_ID, maybeConfiguration(STRIPE_CONNECTED_ID, "123456", ConfigurationPathLevel.ORGANIZATION),
            STRIPE_SECRET_KEY, maybeConfiguration(STRIPE_SECRET_KEY, "abcd", ConfigurationPathLevel.ORGANIZATION),
            STRIPE_PUBLIC_KEY, maybeConfiguration(STRIPE_PUBLIC_KEY, "abcd", ConfigurationPathLevel.ORGANIZATION)
        );
    }

    public static ConfigurationManager.MaybeConfiguration maybeConfiguration(ConfigurationKeys key, String value, ConfigurationPathLevel level) {
        if(value == null) {
            return new ConfigurationManager.MaybeConfiguration(key);
        }
        return new ConfigurationManager.MaybeConfiguration(key, new ConfigurationKeyValuePathLevel(key.getValue(), value, level));
    }
}
