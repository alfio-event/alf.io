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

import alfio.config.authentication.support.OpenIdPrincipal;
import alfio.controller.support.CSPConfigurer;
import alfio.manager.i18n.I18nManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.user.Role;
import alfio.util.Json;
import alfio.util.TemplateManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static alfio.controller.Constants.*;
import static alfio.model.system.ConfigurationKeys.SHOW_PROJECT_BANNER;

@Controller
public class AdminIndexController {

    public record ManifestEntry(List<String> css, String file) {
    }

    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final CSPConfigurer cspConfigurer;
    private final TemplateManager templateManager;
    private final ManifestEntry manifestEntry;
    private final I18nManager i18nManager;

    public AdminIndexController(ConfigurationManager configurationManager,
                                Environment environment,
                                CSPConfigurer cspConfigurer,
                                TemplateManager templateManager,
                                ObjectMapper objectMapper,
                                I18nManager i18nManager) throws IOException {
        this.configurationManager = configurationManager;
        this.environment = environment;
        this.cspConfigurer = cspConfigurer;
        this.templateManager = templateManager;
        this.i18nManager = i18nManager;
        var cpr = new ClassPathResource("/resources/alfio-admin-frontend/.vite/manifest.json");
        if (cpr.exists()) {
            try (var descriptor = cpr.getInputStream()) {
                this.manifestEntry = objectMapper.readValue(descriptor, new TypeReference<Map<String, ManifestEntry>>() {}).get("src/main.ts");
            }
        } else {
            manifestEntry = null;
        }
    }

    @GetMapping({"/admin", "/admin/"})
    public void adminHome(Model model, @Value("${alfio.version}") String version, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        model.addAttribute("alfioVersion", version);
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());

        boolean isDBAuthentication = !(principal instanceof OAuth2AuthenticationToken);
        model.addAttribute("isDBAuthentication", isDBAuthentication);
        if (!isDBAuthentication) {
            var openIdPrincipal = ((OpenIdPrincipal)((OAuth2AuthenticationToken) principal).getPrincipal());
            String idpLogoutRedirectionUrl = openIdPrincipal.idpLogoutRedirectionUrl();
            model.addAttribute("idpLogoutRedirectionUrl", idpLogoutRedirectionUrl);
            model.addAttribute("username", openIdPrincipal.user().email());
        } else {
            model.addAttribute("idpLogoutRedirectionUrl", null);
            model.addAttribute("username", principal.getName());
        }

        Collection<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            .stream().map(GrantedAuthority::getAuthority).toList();

        boolean isAdmin = authorities.contains(Role.ADMIN.getRoleName());
        model.addAttribute("isOwner", isAdmin || authorities.contains(Role.OWNER.getRoleName()));
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("supportedLanguages", i18nManager.getAvailableLanguages());
        //
        addCommonModelAttributes(model, request, version, environment);
        model.addAttribute("displayProjectBanner", isAdmin && configurationManager.getForSystem(SHOW_PROJECT_BANNER).getValueAsBooleanOrDefault());
        //

        model.addAttribute("litAdminStatic", manifestEntry != null);
        if (manifestEntry != null) {
            model.addAttribute("lit-css", manifestEntry.css);
            model.addAttribute("lit-js", manifestEntry.file);
        }

        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = cspConfigurer.addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
            model.addAttribute("render-json", RENDER_JSON.apply(model.asMap()));
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/admin-index.ms"), model.asMap(), os);
        }
    }

    /**
     * {{#render-json}}property-to-render{{/render-json}}
     * If the property does not exist, we render it as 'null'
     */
    static final Function<Map<String, Object>, Mustache.Lambda> RENDER_JSON = model -> (frag, out) -> {
        if (model == null) {
            out.write("null");
            return;
        }

        var property = frag.execute().strip();

        if (model.containsKey(property)) {
            out.write("'" + Json.toJson(model.get(property)) + "'");
        } else {
            out.write("null");
        }
    };
}
