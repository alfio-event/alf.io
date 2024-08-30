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
package alfio.config.authentication.support;

import alfio.manager.payment.MollieConnectManager;
import alfio.manager.payment.OAuthPaymentProviderConnector;
import alfio.manager.payment.StripeConnectManager;
import alfio.manager.user.UserManager;
import alfio.util.TemplateManager;
import alfio.util.oauth2.AccessTokenResponseDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatchers;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import static alfio.manager.payment.MollieConnectManager.MOLLIE_CONNECT_REDIRECT_PATH;
import static alfio.manager.payment.StripeConnectManager.STRIPE_CONNECT_REDIRECT_PATH;

public class PaymentProviderConnectFilter extends GenericFilterBean {

    private static final Logger log = LoggerFactory.getLogger(PaymentProviderConnectFilter.class);
    private static final String CONNECT_ORG = ".connect.org";
    private static final String CONNECT_STATE_PREFIX = ".connect.state.";
    public static final String MOLLIE = "mollie";
    public static final String ADMIN = "/admin";
    public static final String ERROR_MESSAGE = "errorMessage";
    private final TemplateManager templateManager;
    private final RequestMatcher requestMatcher;
    private final RequestMatcher authorizeRequestMatcher;
    private final UserManager userManager;
    private final FlashMapManager flashMapManager;
    private final StripeConnectManager stripeConnectManager;
    private final MollieConnectManager mollieConnectManager;

    public PaymentProviderConnectFilter(TemplateManager templateManager,
                                        UserManager userManager,
                                        StripeConnectManager stripeConnectManager,
                                        MollieConnectManager mollieConnectManager) {
        this.templateManager = templateManager;
        this.userManager = userManager;
        this.stripeConnectManager = stripeConnectManager;
        this.mollieConnectManager = mollieConnectManager;
        this.requestMatcher = new AntPathRequestMatcher("/admin/configuration/payment/{provider}/connect/{orgId}");
        this.authorizeRequestMatcher = RequestMatchers.anyOf(
            new AntPathRequestMatcher(STRIPE_CONNECT_REDIRECT_PATH),
            new AntPathRequestMatcher(MOLLIE_CONNECT_REDIRECT_PATH)
        );
        this.flashMapManager = new SessionFlashMapManager();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        var request = (HttpServletRequest) req;
        var response = (HttpServletResponse) res;
        var matcher = requestMatcher.matcher(request);
        if (matcher.isMatch()) {
            initializeConnect(matcher, request, response);
        } else if (authorizeRequestMatcher.matches(request)) {
            var stateParams = getStateParams(request);
            var session = request.getSession(false);
            if (stateParams.isValid() && session == null) {
                var redirectPath = request.getRequestURI() + "?" + request.getQueryString()+"&p=true";
                log.trace("Session is null. Request URI is: {}", redirectPath);
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                templateManager.renderHtml(
                    new ClassPathResource("/alfio/templates/openid-redirect.ms"),
                    Map.of("redirectPath", redirectPath),
                    response.getWriter()
                );
                response.flushBuffer();
                return;
            } else if (session == null) {
                log.trace("Session is null and a first attempt has been already made. Giving up...");
            } else {
                handleConnectResponse(request, response, session, stateParams);
            }
        }
        chain.doFilter(req, res);
    }

    private static StateParams getStateParams(HttpServletRequest request) {
        var state = request.getParameter("state");
        var code = request.getParameter("code");
        var errorCode = request.getParameter("error");
        var errorDescription = request.getParameter("error_description");
        var processed = request.getParameter("p");
        return new StateParams(state, code, errorCode, errorDescription, processed);
    }


    private void handleConnectResponse(HttpServletRequest request,
                                       HttpServletResponse response,
                                       HttpSession session,
                                       StateParams stateParams) throws IOException {
        try {
            boolean isMollie = request.getRequestURI().equals(MOLLIE_CONNECT_REDIRECT_PATH);
            var provider = isMollie ? MOLLIE : "stripe";
            var orgId = (Integer) session.getAttribute(provider + CONNECT_ORG);
            if (orgId == null || !userManager.isOwnerOfOrganization(SecurityContextHolder.getContext().getAuthentication().getName(), orgId)) {
                response.sendRedirect(ADMIN);
            } else {
                var flashMap = new FlashMap();
                session.removeAttribute(provider + CONNECT_ORG);
                String persistedState = (String) session.getAttribute(provider + CONNECT_STATE_PREFIX + orgId);
                session.removeAttribute(provider + CONNECT_STATE_PREFIX + orgId);
                boolean stateVerified = Objects.equals(persistedState, stateParams.state);
                if(stateVerified && stateParams.code != null) {
                    AccessTokenResponseDetails connectResult = getConnector(provider).storeConnectedAccountId(stateParams.code, orgId);
                    if(connectResult.isSuccess()) {
                        response.sendRedirect("/admin/#/configuration/organization/"+orgId);
                    }
                } else if(stateVerified && StringUtils.isNotEmpty(stateParams.errorCode)) {
                    log.warn("error from {}. {}={}", provider, stateParams.errorCode, stateParams.errorDescription);
                    flashMap.put(ERROR_MESSAGE, Objects.toString(stateParams.errorDescription, stateParams.errorCode));
                    flashMapManager.saveOutputFlashMap(flashMap, request, response);
                    response.sendRedirect(ADMIN);
                } else {
                    flashMap.put(ERROR_MESSAGE, "Couldn't connect your account. Please retry.");
                    flashMapManager.saveOutputFlashMap(flashMap, request, response);
                    response.sendRedirect(ADMIN);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Cannot complete connection with payment provider", e);
            var flashMap = new FlashMap();
            flashMap.put(ERROR_MESSAGE, "Couldn't connect your account. Please retry.");
            flashMapManager.saveOutputFlashMap(flashMap, request, response);
            response.sendRedirect(ADMIN);
        }
    }

    private void initializeConnect(RequestMatcher.MatchResult matcher, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var variables = matcher.getVariables();
        int orgId = Integer.parseInt(variables.get("orgId"));
        String provider = variables.get("provider");
        var username = SecurityContextHolder.getContext().getAuthentication().getName();
        if (("stripe".equals(provider) || MOLLIE.equals(provider)) && userManager.isOwnerOfOrganization(username, orgId)) {
            var session = Objects.requireNonNull(request.getSession(false));
            var connectURL = getConnector(provider).getConnectURL(orgId);
            session.setAttribute(provider+CONNECT_STATE_PREFIX +orgId, connectURL.getState());
            session.setAttribute(provider+CONNECT_ORG, orgId);
            response.sendRedirect(connectURL.getAuthorizationUrl());
        } else {
            response.sendRedirect(ADMIN);
        }
    }

    private OAuthPaymentProviderConnector getConnector(String providerAsString) {
        return MOLLIE.equals(providerAsString) ? mollieConnectManager : stripeConnectManager;
    }

    record StateParams(String state,
                       String code,
                       String errorCode,
                       String errorDescription,
                       String processed) {
        boolean isValid() {
            return processed == null && ((state != null && code != null) || errorCode != null);
        }
    }
}
