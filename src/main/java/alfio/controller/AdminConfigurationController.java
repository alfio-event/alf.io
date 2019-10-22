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

import alfio.manager.payment.StripeConnectManager;
import alfio.manager.payment.stripe.StripeConnectResult;
import alfio.manager.payment.stripe.StripeConnectURL;
import alfio.manager.user.UserManager;
import alfio.model.system.Configuration;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

import static alfio.manager.payment.stripe.StripeConnectURL.CONNECT_REDIRECT_PATH;

@Controller
@AllArgsConstructor
@Log4j2
public class AdminConfigurationController {

    private static final String STRIPE_CONNECT_ORG = "stripe.connect.org";
    private static final String STRIPE_CONNECT_STATE_PREFIX = "stripe.connect.state.";
    private final StripeConnectManager stripeConnectManager;
    private final UserManager userManager;

    @RequestMapping("/admin/configuration/payment/stripe/connect/{orgId}")
    public String redirectToStripeConnect(Principal principal,
                                          @PathVariable("orgId") Integer orgId,
                                          HttpSession session) {
        if(userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), orgId)) {
            StripeConnectURL connectURL = stripeConnectManager.getConnectURL(orgId);
            session.setAttribute(STRIPE_CONNECT_STATE_PREFIX +orgId, connectURL.getState());
            session.setAttribute(STRIPE_CONNECT_ORG, orgId);
            return "redirect:" + connectURL.getAuthorizationURL();
        }
        return "redirect:/admin/";
    }


    @RequestMapping(CONNECT_REDIRECT_PATH)
    public String authorize(Principal principal,
                            @RequestParam("state") String state,
                            @RequestParam(value = "code", required = false) String code,
                            @RequestParam(value = "error", required = false) String errorCode,
                            @RequestParam(value = "error_description", required = false) String errorDescription,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        return Optional.ofNullable(session.getAttribute(STRIPE_CONNECT_ORG))
            .map(Integer.class::cast)
            .filter(orgId -> userManager.isOwnerOfOrganization(userManager.findUserByUsername(principal.getName()), orgId))
            .map(orgId -> {
                session.removeAttribute(STRIPE_CONNECT_ORG);
                String persistedState = (String) session.getAttribute(STRIPE_CONNECT_STATE_PREFIX + orgId);
                session.removeAttribute(STRIPE_CONNECT_STATE_PREFIX + orgId);
                boolean stateVerified = Objects.equals(persistedState, state);
                if(stateVerified && code != null) {
                    StripeConnectResult connectResult = stripeConnectManager.storeConnectedAccountId(code, Configuration.from(orgId));
                    if(connectResult.isSuccess()) {
                        return "redirect:/admin/#/configuration/organization/"+orgId;
                    }
                } else if(stateVerified && StringUtils.isNotEmpty(errorCode)) {
                    log.warn("error from stripe. {}={}", errorCode, errorDescription);
                    redirectAttributes.addFlashAttribute("errorMessage", StringUtils.defaultString(errorDescription, errorCode));
                    return "redirect:/admin/";
                }
                redirectAttributes.addFlashAttribute("errorMessage", "Couldn't connect your account. Please retry.");
                return "redirect:/admin/";
            }).orElse("redirect:/admin/");
    }

}
