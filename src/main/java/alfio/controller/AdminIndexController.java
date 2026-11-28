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
import alfio.controller.support.AdminFrontendManifest;
import alfio.controller.support.CSPConfigurer;
import alfio.manager.i18n.I18nManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.user.Role;
import alfio.util.Json;
import alfio.util.TemplateManager;
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
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static alfio.controller.Constants.*;
import static alfio.model.system.ConfigurationKeys.BASE_URL;
import static alfio.model.system.ConfigurationKeys.SHOW_PROJECT_BANNER;

@Controller
public class AdminIndexController {

    private static final String USERNAME = "username";
    private static final String IDP_LOGOUT_REDIRECTION_URL = "idpLogoutRedirectionUrl";
    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final CSPConfigurer cspConfigurer;
    private final TemplateManager templateManager;
    private final AdminFrontendManifest frontendManifest;
    private final I18nManager i18nManager;

    public AdminIndexController(ConfigurationManager configurationManager,
                                Environment environment,
                                CSPConfigurer cspConfigurer,
                                TemplateManager templateManager,
                                AdminFrontendManifest frontendManifest,
                                I18nManager i18nManager) {
        this.configurationManager = configurationManager;
        this.environment = environment;
        this.cspConfigurer = cspConfigurer;
        this.templateManager = templateManager;
        this.frontendManifest = frontendManifest;
        this.i18nManager = i18nManager;
    }

    /**
     * The admin is a single page application: every page below /admin is rendered by the same shell, which routes on
     * the client. Unmatched API and translation requests must still answer 404.
     */
    @GetMapping({"/admin", "/admin/", "/admin/{*path}"})
    public void adminHome(Model model, @Value("${alfio.version}") String version, HttpServletRequest request, HttpServletResponse response, Principal principal) throws IOException {
        if (isNotAnAdminPage(request)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        model.addAttribute("alfioVersion", version);
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());

        boolean isDBAuthentication = !(principal instanceof OAuth2AuthenticationToken);
        model.addAttribute("isDBAuthentication", isDBAuthentication);
        if (!isDBAuthentication) {
            var openIdPrincipal = ((OpenIdPrincipal)((OAuth2AuthenticationToken) principal).getPrincipal());
            String idpLogoutRedirectionUrl = openIdPrincipal.idpLogoutRedirectionUrl();
            model.addAttribute(IDP_LOGOUT_REDIRECTION_URL, idpLogoutRedirectionUrl);
            model.addAttribute(USERNAME, openIdPrincipal.user().email());
        } else {
            model.addAttribute(IDP_LOGOUT_REDIRECTION_URL, null);
            model.addAttribute(USERNAME, principal.getName());
        }

        Collection<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
            .stream().map(GrantedAuthority::getAuthority).toList();

        boolean isAdmin = authorities.contains(Role.ADMIN.getRoleName());
        model.addAttribute("isOwner", isAdmin || authorities.contains(Role.OWNER.getRoleName()));
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("supportedLanguages", i18nManager.getAvailableLanguages());
        // resolved by MvcConfiguration#localeResolver: the language picker (?lang=), then the browser's languages
        var language = RequestContextUtils.getLocale(request).getLanguage();
        model.addAttribute("language", language);
        // the version busts the browser cache of the translations at every release
        model.addAttribute("translationsUrl", request.getContextPath() + "/api/v2/admin/i18n/bundle/" + language
            + "?v=" + URLEncoder.encode(version, StandardCharsets.UTF_8));
        //
        addCommonModelAttributes(model, request, environment);
        model.addAttribute("displayProjectBanner", isAdmin && configurationManager.getForSystem(SHOW_PROJECT_BANNER).getValueAsBooleanOrDefault());
        model.addAttribute("baseUrl", configurationManager.getForSystem(BASE_URL).getValueOrDefault(""));
        //

        var mainEntry = frontendManifest.mainEntry();
        model.addAttribute("litAdminStatic", mainEntry.isPresent());
        mainEntry.ifPresent(entry -> {
            model.addAttribute("lit-css", entry.css());
            model.addAttribute("lit-js", entry.file());
            var polyfills = frontendManifest.polyfills();
            model.addAttribute("litPolyfills", polyfills.isPresent());
            polyfills.ifPresent(p -> model.addAttribute("lit-polyfills-js", p.file()));
            // imported by main.ts after the translations: preloaded, so that it downloads in parallel
            frontendManifest.appEntry().ifPresent(app -> model.addAttribute("lit-app-js", app.file()));
        });

        model.addAttribute("adminConfig", Json.toJson(adminConfig(model.asMap())));

        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = cspConfigurer.addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
            model.addAttribute("render-json", RENDER_JSON.apply(model.asMap()));
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/admin-index.ms"), model.asMap(), os);
        }
    }

    static boolean isNotAnAdminPage(HttpServletRequest request) {
        var adminRoot = request.getContextPath() + "/admin/";
        var uri = request.getRequestURI();
        return uri.equals(adminRoot + "api") || uri.startsWith(adminRoot + "api/")
            || uri.equals(adminRoot + "i18n") || uri.startsWith(adminRoot + "i18n/");
    }

    /**
     * The values the client-side shell (frontend/admin, <alfio-admin-app>) needs, see admin-config.ts
     */
    static Map<String, Object> adminConfig(Map<String, Object> model) {
        var config = new HashMap<String, Object>();
        List.of("contextPath",  "isOwner", "isAdmin", "isDBAuthentication", USERNAME, IDP_LOGOUT_REDIRECTION_URL,
                "errorMessage", "displayProjectBanner", "alfioVersion", "basicConfigurationNeeded", "supportedLanguages",
                "baseUrl", "devModeEnabled", "prodModeEnabled", "language", "translationsUrl")
            .forEach(key -> config.put(key, model.get(key)));
        return config;
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
