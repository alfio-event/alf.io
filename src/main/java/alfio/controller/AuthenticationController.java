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

import alfio.config.authentication.support.AuthenticationConstants;
import alfio.controller.support.CSPConfigurer;
import alfio.controller.support.UserStatus;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.util.TemplateManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.EnumSet;

import static alfio.controller.Constants.*;
import static alfio.model.system.ConfigurationKeys.ENABLE_CAPTCHA_FOR_LOGIN;
import static alfio.model.system.ConfigurationKeys.RECAPTCHA_API_KEY;
import static com.github.scribejava.core.model.OAuthConstants.NONCE;

@Controller
public class AuthenticationController {

    private static final String REDIRECT_ADMIN = "redirect:/admin/";

    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final CSPConfigurer cspConfigurer;
    private final TemplateManager templateManager;

    public AuthenticationController(ConfigurationManager configurationManager,
                                    Environment environment,
                                    CSPConfigurer cspConfigurer,
                                    TemplateManager templateManager) {
        this.configurationManager = configurationManager;
        this.environment = environment;
        this.cspConfigurer = cspConfigurer;
        this.templateManager = templateManager;
    }

    @GetMapping(AuthenticationConstants.AUTHENTICATION_STATUS)
    public ResponseEntity<UserStatus> authenticationStatus(Principal principal,
                                                           @Value("${alfio.version}") String version) {

        return ResponseEntity.ok(new UserStatus(
            principal != null,
            principal != null ? principal.getName() : null,
            version,
            demoModeEnabled(environment),
            devModeEnabled(environment),
            prodModeEnabled(environment)
        ));
    }

    @GetMapping("/authentication")
    public void getLoginPage(@RequestParam(value="failed", required = false) String failed,
                             @RequestParam(value = "recaptchaFailed", required = false) String recaptchaFailed,
                             Model model,
                             Principal principal,
                             HttpServletRequest request,
                             HttpServletResponse response,
                             @Value("${alfio.version}") String version) throws IOException {

        if(principal != null) {
            response.sendRedirect("/admin/");
            return;
        }
        model.addAttribute("failed", failed != null);
        model.addAttribute("recaptchaFailed", recaptchaFailed != null);
        model.addAttribute("hasRecaptchaApiKey", false);

        //
        addCommonModelAttributes(model, request, version, environment);
        model.addAttribute("request", request);

        //

        var configuration = configurationManager.getFor(EnumSet.of(RECAPTCHA_API_KEY, ENABLE_CAPTCHA_FOR_LOGIN), ConfigurationLevel.system());

        configuration.get(RECAPTCHA_API_KEY).getValue()
            .filter(key -> configuration.get(ENABLE_CAPTCHA_FOR_LOGIN).getValueAsBooleanOrDefault())
            .ifPresent(key -> {
                model.addAttribute("hasRecaptchaApiKey", true);
                model.addAttribute("recaptchaApiKey", key);
            });
        try (var os = response.getOutputStream()) {
            response.setContentType(TEXT_HTML_CHARSET_UTF_8);
            response.setCharacterEncoding(UTF_8);
            var nonce = cspConfigurer.addCspHeader(response, false);
            model.addAttribute(NONCE, nonce);
            templateManager.renderHtml(new ClassPathResource("alfio/web-templates/login.ms"), model.asMap(), os);
        }
    }

    @PostMapping("/authenticate")
    public String doLogin() {
        return REDIRECT_ADMIN;
    }

}
