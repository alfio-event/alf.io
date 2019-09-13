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
package alfio.controller.api.v2;

import alfio.config.Initializer;
import alfio.controller.api.v2.model.AlfioInfo;
import alfio.controller.api.v2.model.AnalyticsConfiguration;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import lombok.AllArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Set;

@RestController
@RequestMapping("/api/v2/")
@AllArgsConstructor
public class InfoApiController {

    private final Environment environment;
    private final ConfigurationManager configurationManager;

    @GetMapping("info")
    public AlfioInfo getInfo(HttpSession session) {

        var demoMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO));
        var devMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV));
        var prodMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));


        var conf = configurationManager.getFor(Set.of(ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE, ConfigurationKeys.GOOGLE_ANALYTICS_KEY), ConfigurationLevel.system());

        var analyticsConf = AnalyticsConfiguration.build(conf, session);

        return new AlfioInfo(demoMode, devMode, prodMode, analyticsConf);
    }
}
