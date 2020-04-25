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
package alfio.manager;

import alfio.manager.system.OpenIdAuthenticationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OpenIdAuthenticationManagerTest {
    private static final String DOMAIN = "domain_test";
    private static final String CLIENT_ID = "123";
    private static final String CLIENT_SECRET = "1234";
    private static final String CALLBACK_URI = "callback";
    private static final String AUTHENTICATION_URL = "/auth";
    private static final String CLAIMS_URI = "/claims";
    private static final String CONTENT_TYPE = "application/json";
    private static final String GROUPS_NAME = "groups";
    private static final String ALFIO_GROUPS_NAME = "alfio-groups";
    private static final String LOGOUT_URL = "/logoutUrl";
    private static final String LOGOUT_REDIRECT_URL = "logoutRedirectUrl";

    private OpenIdAuthenticationManager authenticationManager;
    private Environment environment;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getProperty("openid.domain")).thenReturn(DOMAIN);
        when(environment.getProperty("openid.clientId")).thenReturn(CLIENT_ID);
        when(environment.getProperty("openid.clientSecret")).thenReturn(CLIENT_SECRET);
        when(environment.getProperty("openid.callbackURI")).thenReturn(CALLBACK_URI);
        when(environment.getProperty("openid.authenticationUrl")).thenReturn(AUTHENTICATION_URL);
        when(environment.getProperty("openid.tokenEndpoint")).thenReturn(CLAIMS_URI);
        when(environment.getProperty("openid.contentType")).thenReturn(CONTENT_TYPE);
        when(environment.getProperty("openid.rolesParameter")).thenReturn(GROUPS_NAME);
        when(environment.getProperty("openid.alfioGroupsParameter")).thenReturn(ALFIO_GROUPS_NAME);
        when(environment.getProperty("openid.logoutUrl")).thenReturn(LOGOUT_URL);
        when(environment.getProperty("openid.logoutRedirectUrl")).thenReturn(LOGOUT_REDIRECT_URL);
        authenticationManager = new OpenIdAuthenticationManager(environment, null, null);
    }

    @Test
    public void oauth2_authorize_url_test() {
        String redirectURL = authenticationManager.buildAuthorizeUrl();
        String expectedURL = "https://domain_test/auth?redirect_uri=callback&client_id=123&scope=openid+email+profile+groups+alfio-groups&response_type=code";
        Assert.assertEquals(expectedURL, redirectURL);
    }

    @Test
    public void oauth2_claims_url_test() {
        String claimsUrl = authenticationManager.buildClaimsRetrieverUrl();
        String expectedURL = "https://domain_test/claims";
        Assert.assertEquals(expectedURL, claimsUrl);
    }

    @Test
    public void oauth2_build_logoutUrl() {
        String logoutUrl = authenticationManager.buildLogoutUrl();
        String expectedURL = "https://domain_test/logoutUrl?redirect_uri=logoutRedirectUrl";
        Assert.assertEquals(expectedURL, logoutUrl);
    }

    @Test
    public void oauth2_json_build_body_test() throws JsonProcessingException {
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> expectedBody = mapper.readValue(
                "{\"code\":\"code\",\"redirect_uri\":\"callback\",\"grant_type\":\"authorization_code\",\"client_secret\":\"1234\",\"client_id\":\"123\"}", Map.class);
        Assert.assertEquals(expectedBody, mapper.readValue(body, Map.class));
    }

    @Test
    public void oauth2_form_url_encoded_build_body_test() throws JsonProcessingException {
        String contentType = "application/x-www-form-urlencoded";
        when(environment.getProperty("openid.contentType")).thenReturn(contentType);
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        String expectedBody = "grant_type=authorization_code&code=code&client_id=123&client_secret=1234&redirect_uri=callback";
        Assert.assertEquals(expectedBody, body);
    }

    @Test
    public void oauth2_get_scopes() {
        List<String> expectedScopes = Arrays.asList("openid", "email", "profile", GROUPS_NAME, ALFIO_GROUPS_NAME);
        List<String> actualScopes = authenticationManager.getScopes();
        Assert.assertEquals(expectedScopes, actualScopes);
    }
}