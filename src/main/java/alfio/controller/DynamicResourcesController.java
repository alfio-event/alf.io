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
package alfio.controller;

import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Controller
public class DynamicResourcesController {

    private static final String GOOGLE_ANALYTICS_SCRIPT = "var _gaq = _gaq || [];_gaq.push(['_setAccount', '%s']);_gaq.push(['_trackPageview']);";
    private static final String EMPTY = "(function(){})();";
    private final ConfigurationManager configurationManager;

    @Autowired
    public DynamicResourcesController(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @RequestMapping("/resources/js/google-analytics")
    public void getGoogleAnalyticsScript(HttpServletResponse response) throws IOException {
        response.setContentType("application/javascript");
        final Optional<String> id = configurationManager.getStringConfigValue(ConfigurationKeys.GOOGLE_ANALYTICS_KEY);
        response.getWriter().write(id.map(x -> String.format(GOOGLE_ANALYTICS_SCRIPT, x)).orElse(EMPTY));
    }
}
