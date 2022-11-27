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

import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;

import javax.servlet.http.HttpServletRequest;

public interface Constants {
    String TEXT_HTML_CHARSET_UTF_8 = "text/html;charset=UTF-8";
    String UTF_8 = "UTF-8";
    String NONCE = "nonce";
    String REDIRECT = "redirect:";
    String EVENT_SHORT_NAME = "eventShortName";
    String NOT_FOUND = "not-found";
    String CONTENT = "content";
    String PROPERTY = "property";

    static void addCommonModelAttributes(Model model, HttpServletRequest request, String version, Environment environment) {
        var contextPath = StringUtils.appendIfMissing(request.getContextPath(), "/") + version;
        model.addAttribute("contextPath", contextPath);
        model.addAttribute("demoModeEnabled", demoModeEnabled(environment));
        model.addAttribute("devModeEnabled", devModeEnabled(environment));
        model.addAttribute("prodModeEnabled", prodModeEnabled(environment));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
    }

    static boolean prodModeEnabled(Environment environment) {
        return profileActive(environment, Initializer.PROFILE_LIVE);
    }

    static boolean devModeEnabled(Environment environment) {
        return profileActive(environment, Initializer.PROFILE_DEV);
    }

    static boolean demoModeEnabled(Environment environment) {
        return profileActive(environment, Initializer.PROFILE_DEMO);
    }

    private static boolean profileActive(Environment environment, String profile) {
        return environment.acceptsProfiles(Profiles.of(profile));
    }

}
