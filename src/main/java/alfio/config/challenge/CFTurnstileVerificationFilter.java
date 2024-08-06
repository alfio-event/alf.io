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
package alfio.config.challenge;

import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import static alfio.util.HttpUtils.APPLICATION_JSON;
import static alfio.util.HttpUtils.CONTENT_TYPE;

public class CFTurnstileVerificationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(CFTurnstileVerificationFilter.class);
    private static final URI SITEVERIFY = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
    private final ConfigurationManager configurationManager;
    private final RequestMatcher requestMatcher;
    private final RestClient restClient;


    public CFTurnstileVerificationFilter(ConfigurationManager configurationManager,
                                         RequestMatcher requestMatcher) {
        this.configurationManager = configurationManager;
        this.requestMatcher = requestMatcher;
        this.restClient = RestClient.builder()
            .baseUrl("https://challenges.cloudflare.com")
            .build();

    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (requestMatcher.matches(request)) {
            log.trace("Request matching. Checking if turnstile is enabled.");
            var configuration = configurationManager.getTurnstileConfiguration();
            if (configuration.get(ConfigurationKeys.CF_TURNSTILE_ENABLED).getValueAsBooleanOrDefault()) {
                log.trace("Turnstile is enabled. Proceeding with validation.");
                var challenge = request.getHeader("Alfio-Verification");
                if (!verifyChallenge(challenge, configuration, request)) {
                    log.trace("verification failed. Returning 403");
                    response.setHeader("Alfio-Verification-Missing", "turnstile");
                    response.sendError(HttpStatus.FORBIDDEN.value());
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean verifyChallenge(String challenge,
                                    Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration,
                                    HttpServletRequest request) {

        boolean challengeEmpty = StringUtils.isBlank(challenge);
        if (challengeEmpty && preClearanceEnabledAndPresent(configuration, request)) {
            // if pre-clearance is enabled, and we have received the relevant cookie, the request can be considered valid
            // even if the challenge code is missing.
            log.trace("Validation is SUCCESSFUL because cf-clearance is enabled and the relevant cookie is present in the request.");
            return true;
        } else if (challengeEmpty) {
            // if pre-clearance is NOT enabled, we require challenge to be present
            log.trace("Validation is NOT SUCCESSFUL because token is missing.");
            return false;
        }

        var secret = configuration.get(ConfigurationKeys.CF_TURNSTILE_SECRET_KEY).getRequiredValue();
        Map<String, String> payload = new HashMap<>();
        payload.put("secret", secret);
        payload.put("response", challenge);
        // additional security: if we are behind CloudFlare proxy, we include the remote IP in the payload
        if (request.getHeader("CF-Connecting-IP") != null) {
            payload.put("remoteip", request.getHeader("CF-Connecting-IP"));
        }
        var response = restClient.post()
            .uri(SITEVERIFY)
            .header(CONTENT_TYPE, APPLICATION_JSON)
            .body(Json.toJson(payload))
            .retrieve()
            .toEntity(TurnstileResponse.class);
        log.trace("Received {} response from siteverify", response.getStatusCode());
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.trace("Validation is NOT SUCCESSFUL because response from siteverify is not successful.");
            return false;
        }
        var body = Objects.requireNonNull(response.getBody());
        if (body.success) {
            log.trace("Validation is SUCCESSFUL.");
            return true;
        }
        log.debug("Validation is NOT SUCCESSFUL because siteverify reported token as not valid.");
        return false;
    }

    private boolean preClearanceEnabledAndPresent(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configuration,
                                                  HttpServletRequest request) {
        if (!configuration.get(ConfigurationKeys.CF_TURNSTILE_PRE_CLEARANCE).getValueAsBooleanOrDefault()) {
            return false;
        }
        return Arrays.stream(request.getCookies()).anyMatch(c -> c.getName().equals("cf_clearance") && StringUtils.isNotBlank(c.getValue()));
    }

    record TurnstileResponse(@JsonProperty("success") boolean success,
                             @JsonProperty("action") String action,
                             @JsonProperty("cdata") String cdata,
                             @JsonProperty("error-codes") List<String> errorCodes) {}
}
