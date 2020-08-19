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
package alfio.controller.api.v2.model;

import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpSession;
import java.util.Map;

import static alfio.model.system.ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE;
import static alfio.model.system.ConfigurationKeys.GOOGLE_ANALYTICS_KEY;

@AllArgsConstructor
@Getter
public class AnalyticsConfiguration {
    private final String googleAnalyticsKey;
    private final boolean googleAnalyticsScrambledInfo; //<- see GOOGLE_ANALYTICS_ANONYMOUS_MODE
    private final String clientId;


    public static AnalyticsConfiguration build(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> conf, HttpSession session) {
        var googAnalyticsKey = StringUtils.trimToNull(conf.get(GOOGLE_ANALYTICS_KEY).getValueOrNull());
        var googAnalyticsScrambled = conf.get(GOOGLE_ANALYTICS_ANONYMOUS_MODE).getValueAsBooleanOrDefault();

        var sessionId = session.getId();
        var clientId = googAnalyticsKey != null && googAnalyticsScrambled && sessionId != null ? DigestUtils.sha256Hex(sessionId) : null;
        return new AnalyticsConfiguration(googAnalyticsKey, googAnalyticsScrambled, clientId);
    }
}
