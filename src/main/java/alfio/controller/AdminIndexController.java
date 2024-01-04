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

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.controller.support.CSPConfigurer;
import alfio.manager.system.ConfigurationManager;
import alfio.model.user.Role;
import alfio.util.TemplateManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;

import static alfio.controller.Constants.*;
import static alfio.model.system.ConfigurationKeys.SHOW_PROJECT_BANNER;

@Controller
public class AdminIndexController {

    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final CSPConfigurer cspConfigurer;
    private final TemplateManager templateManager;

    public AdminIndexController(ConfigurationManager configurationManager,
                                Environment environment,
                                CSPConfigurer cspConfigurer,
                                TemplateManager templateManager) {
        this.configurationManager = configurationManager;
        this.environment = environment;
        this.cspConfigurer = cspConfigurer;
        this.templateManager = templateManager;
    }

    @GetMapping("/admin")
    public void adminHome(Model model, @Value("${alfio.version}") String version, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        model.addAttribute("alfioVersion", version);
        model.addAttribute("username", principal.getName());
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());

        boolean isDBAuthentication = !(principal instanceof OpenIdAlfioAuthentication);
        model.addAttribute("isDBAuthentication", isDBAuthentication);
        if (!isDBAuthentication) {
            String idpLogoutRedirectionUrl = ((OpenIdAlfioAuthentication) SecurityContextHolder.getContext().getAuthentication()).getIdpLogoutRedirectionUrl();
            model.addAttribute("idpLogoutRedirectionUrl", idpLogoutRedirectionUrl);
        } else {
            model.addAttribute("idpLogoutRedirectionUrl", null);
        }

        Collection<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            .stream().map(GrantedAuthority::getAuthority).toList();

        boolean isAdmin = authorities.contains(Role.ADMIN.getRoleName());
        model.addAttribute("isOwner", isAdmin || authorities.contains(Role.OWNER.getRoleName()));
        model.addAttribute("isAdmin", isAdmin);
        //
        addCommonModelAttributes(model, request, version, environment);
        model.addAttribute("displayProjectBanner", isAdmin && configurationManager.getForSystem(SHOW_PROJECT_BANNER).getValueAsBooleanOrDefault());
        //

        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = cspConfigurer.addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/admin-index.ms"), model.asMap(), os);
        }
    }
}
