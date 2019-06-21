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
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.system.ConfigurationKeys;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;

import static alfio.model.system.ConfigurationKeys.ENABLE_CAPTCHA_FOR_LOGIN;

@Controller
@AllArgsConstructor
public class IndexController {

    private static final String REDIRECT_ADMIN = "redirect:/admin/";

    private final ConfigurationManager configurationManager;
    private final Environment environment;
    private final UserManager userManager;


    @RequestMapping(value = "/", method = RequestMethod.HEAD)
    public ResponseEntity<String> replyToProxy() {
        return ResponseEntity.ok("Up and running!");
    }

    @RequestMapping(value = "/healthz", method = RequestMethod.GET)
    public ResponseEntity<String> replyToK8s() {
        return ResponseEntity.ok("Up and running!");
    }


    //url defined in the angular app in app-routing.module.ts
    /**
    <pre>
    { path: '', component: EventListComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName', component: EventDisplayComponent, canActivate: [LanguageGuard] },
    { path: 'event/:eventShortName/reservation/:reservationId', component: ReservationComponent, canActivate: [LanguageGuard, ReservationGuard], children: [
        { path: 'book', component: BookingComponent, canActivate: [ReservationGuard] },
        { path: 'overview', component: OverviewComponent, canActivate: [ReservationGuard] },
        { path: 'waitingPayment', redirectTo: 'waiting-payment'},
        { path: 'waiting-payment', component: OfflinePaymentComponent, canActivate: [ReservationGuard] },
        { path: 'processing-payment', component: ProcessingPaymentComponent, canActivate: [ReservationGuard] },
        { path: 'success', component: SuccessComponent, canActivate: [ReservationGuard]}
    ]},
    { path: 'event/:eventShortName/ticket/:ticketId/view', component: ViewTicketComponent, canActivate: [LanguageGuard] }
    </pre>

     */
    @RequestMapping(value =  {
        "/",
        "/event/{eventShortName}",
        "/event/{eventShortName}/reservation/{reservationId}",
        "/event/{eventShortName}/reservation/{reservationId}/book",
        "/event/{eventShortName}/reservation/{reservationId}/overview",
        "/event/{eventShortName}/reservation/{reservationId}/waitingPayment",
        "/event/{eventShortName}/reservation/{reservationId}/waiting-payment",
        "/event/{eventShortName}/reservation/{reservationId}/processing-payment",
        "/event/{eventShortName}/reservation/{reservationId}/success",
        "/event/{eventShortName}/ticket/{ticketId}/view"
    }, method = RequestMethod.GET)
    public void replyToIndex(HttpServletResponse response) throws IOException {
        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        try (var is = new ClassPathResource("alfio-public-frontend-index.html").getInputStream(); var os = response.getOutputStream()) {
            is.transferTo(os);
        }
    }


    // login related
    @RequestMapping(value="/authentication", method = RequestMethod.GET)
    public String getLoginPage(@RequestParam(value="failed", required = false) String failed, @RequestParam(value = "recaptchaFailed", required = false) String recaptchaFailed, Model model, Principal principal, HttpServletRequest request) {
        if(principal != null) {
            return REDIRECT_ADMIN;
        }
        model.addAttribute("failed", failed != null);
        model.addAttribute("recaptchaFailed", recaptchaFailed != null);
        model.addAttribute("hasRecaptchaApiKey", false);

        //
        model.addAttribute("request", request);
        model.addAttribute("demoModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        model.addAttribute("devModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV)));
        model.addAttribute("prodModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        //

        configurationManager.getFor(ConfigurationKeys.RECAPTCHA_API_KEY).getValue()
            .filter(key -> configurationManager.getFor(ENABLE_CAPTCHA_FOR_LOGIN).getValueAsBooleanOrDefault(true))
            .ifPresent(key -> {
                model.addAttribute("hasRecaptchaApiKey", true);
                model.addAttribute("recaptchaApiKey", key);
            });

        return "/login/login";
    }

    @RequestMapping(value="/authenticate", method = RequestMethod.POST)
    public String doLogin() {
        return REDIRECT_ADMIN;
    }
    //


    // admin index
    @RequestMapping("/admin")
    public String adminHome(Model model, @Value("${alfio.version}") String version, HttpServletRequest request, Principal principal) {
        model.addAttribute("alfioVersion", version);
        model.addAttribute("username", principal.getName());
        model.addAttribute("basicConfigurationNeeded", configurationManager.isBasicConfigurationNeeded());
        model.addAttribute("isAdmin", principal.getName().equals("admin"));
        model.addAttribute("isOwner", userManager.isOwner(userManager.findUserByUsername(principal.getName())));
        //
        model.addAttribute("request", request);
        model.addAttribute("demoModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        model.addAttribute("devModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV)));
        model.addAttribute("prodModeEnabled", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE)));
        model.addAttribute(WebSecurityConfig.CSRF_PARAM_NAME, request.getAttribute(CsrfToken.class.getName()));
        //
        return "/admin/index";
    }
}
