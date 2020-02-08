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

import alfio.manager.payment.MollieConnectManager;
import alfio.manager.payment.OAuthPaymentProviderConnector;
import alfio.manager.payment.StripeConnectManager;
import alfio.manager.user.UserManager;
import alfio.util.oauth2.AccessTokenResponseDetails;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static alfio.manager.payment.MollieConnectManager.MOLLIE_CONNECT_REDIRECT_PATH;
import static alfio.manager.payment.StripeConnectManager.STRIPE_CONNECT_REDIRECT_PATH;

@Controller
@AllArgsConstructor
@Log4j2
public class AdminConfigurationController {

    private static final String CONNECT_ORG = ".connect.org";
    private static final String CONNECT_STATE_PREFIX = ".connect.state.";
    private static final String REDIRECT_ADMIN = "redirect:/admin/";
    private static final List<String> CONNECT_PROVIDERS = List.of("stripe", "mollie");

    private final StripeConnectManager stripeConnectManager;
    private final MollieConnectManager mollieConnectManager;
    private final UserManager userManager;

    @GetMapping("/admin/configuration/payment/{provider}/connect/{orgId}")
    public String oAuthRedirectToAuthorizationURL(Principal principal,
                                                  @PathVariable("orgId") Integer orgId,
                                                  @PathVariable("provider") String provider,
                                                  HttpSession session) {
        if(CONNECT_PROVIDERS.contains(provider) && userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), orgId)) {
            var connectURL = getConnector(provider).getConnectURL(orgId);
            session.setAttribute(provider+CONNECT_STATE_PREFIX +orgId, connectURL.getState());
            session.setAttribute(provider+CONNECT_ORG, orgId);
            return "redirect:" + connectURL.getAuthorizationUrl();
        }
        return REDIRECT_ADMIN;
    }


    @GetMapping({ STRIPE_CONNECT_REDIRECT_PATH, MOLLIE_CONNECT_REDIRECT_PATH })
    public String authorize(Principal principal,
                            @RequestParam("state") String state,
                            @RequestParam(value = "code", required = false) String code,
                            @RequestParam(value = "error", required = false) String errorCode,
                            @RequestParam(value = "error_description", required = false) String errorDescription,
                            HttpServletRequest request,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        boolean isMollie = request.getRequestURI().equals(MOLLIE_CONNECT_REDIRECT_PATH);
        var provider = isMollie ? "mollie" : "stripe";

        return Optional.ofNullable(session.getAttribute(provider + CONNECT_ORG))
            .map(Integer.class::cast)
            .filter(orgId -> userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), orgId))
            .map(orgId -> {
                session.removeAttribute(provider + CONNECT_ORG);
                String persistedState = (String) session.getAttribute(provider + CONNECT_STATE_PREFIX + orgId);
                session.removeAttribute(provider + CONNECT_STATE_PREFIX + orgId);
                boolean stateVerified = Objects.equals(persistedState, state);
                if(stateVerified && code != null) {
                    AccessTokenResponseDetails connectResult = getConnector(provider).storeConnectedAccountId(code, orgId);
                    if(connectResult.isSuccess()) {
                        return "redirect:/admin/#/configuration/organization/"+orgId;
                    }
                } else if(stateVerified && StringUtils.isNotEmpty(errorCode)) {
                    log.warn("error from {}. {}={}", provider, errorCode, errorDescription);
                    redirectAttributes.addFlashAttribute("errorMessage", StringUtils.defaultString(errorDescription, errorCode));
                    return REDIRECT_ADMIN;
                }
                redirectAttributes.addFlashAttribute("errorMessage", "Couldn't connect your account. Please retry.");
                return REDIRECT_ADMIN;
            }).orElse(REDIRECT_ADMIN);
    }

    private OAuthPaymentProviderConnector getConnector(String providerAsString) {
        return "mollie".equals(providerAsString) ? mollieConnectManager : stripeConnectManager;
    }

}
