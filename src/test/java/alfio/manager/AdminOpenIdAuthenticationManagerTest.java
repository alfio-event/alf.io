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

import alfio.manager.openid.AdminOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.util.Json;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdminOpenIdAuthenticationManagerTest {
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

    private AdminOpenIdAuthenticationManager authenticationManager;
    private Environment environment;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getProperty(eq("openid.domain"))).thenReturn(DOMAIN);
        when(environment.getProperty(eq("openid.clientId"))).thenReturn(CLIENT_ID);
        when(environment.getProperty(eq("openid.clientSecret"))).thenReturn(CLIENT_SECRET);
        when(environment.getProperty(eq("openid.callbackURI"), anyString())).thenReturn(CALLBACK_URI);
        when(environment.getProperty(eq("openid.authenticationUrl"))).thenReturn(AUTHENTICATION_URL);
        when(environment.getProperty(eq("openid.tokenEndpoint"), anyString())).thenReturn(CLAIMS_URI);
        when(environment.getProperty(eq("openid.contentType"), anyString())).thenReturn(CONTENT_TYPE);
        when(environment.getProperty(eq("openid.rolesParameter"))).thenReturn(GROUPS_NAME);
        when(environment.getProperty(eq("openid.alfioGroupsParameter"))).thenReturn(ALFIO_GROUPS_NAME);
        when(environment.getProperty(eq("openid.logoutUrl"))).thenReturn(LOGOUT_URL);
        when(environment.getProperty(eq("openid.logoutRedirectUrl"), anyString())).thenReturn(LOGOUT_REDIRECT_URL);
        var configurationManager = mock(ConfigurationManager.class);
        when(configurationManager.getFor(eq(ConfigurationKeys.BASE_URL), any()))
            .thenReturn(new ConfigurationManager.MaybeConfiguration(ConfigurationKeys.BASE_URL, new ConfigurationKeyValuePathLevel("", "blabla", ConfigurationPathLevel.SYSTEM)));
        authenticationManager = new AdminOpenIdAuthenticationManager(environment, null, configurationManager, null, null, null, null, null, null, null, new Json());
    }

    @Test
    public void oauth2_authorize_url_test() {
        var state = "this-is-the-state";
        String redirectURL = authenticationManager.buildAuthorizeUrl(state);
        String expectedURL = "https://domain_test/auth?redirect_uri=callback&client_id=123&state="+state+"&scope=openid+email+profile+groups+alfio-groups+given_name+family_name&response_type=code";
        assertEquals(expectedURL, redirectURL);
    }

    @Test
    public void oauth2_claims_url_test() {
        String claimsUrl = authenticationManager.buildClaimsRetrieverUrl();
        String expectedURL = "https://domain_test/claims";
        assertEquals(expectedURL, claimsUrl);
    }

    @Test
    public void oauth2_build_logoutUrl() {
        String logoutUrl = authenticationManager.buildLogoutUrl();
        String expectedURL = "https://domain_test/logoutUrl?redirect_uri=logoutRedirectUrl";
        assertEquals(expectedURL, logoutUrl);
    }

    @Test
    public void oauth2_json_build_body_test() throws JsonProcessingException {
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> expectedBody = mapper.readValue(
            "{\"code\":\"code\",\"redirect_uri\":\"callback\",\"grant_type\":\"authorization_code\",\"client_secret\":\"1234\",\"client_id\":\"123\"}", new TypeReference<>() {
            });
        assertEquals(expectedBody, mapper.readValue(body, Map.class));
    }

    @Test
    public void oauth2_form_url_encoded_build_body_test() throws JsonProcessingException {
        String contentType = "application/x-www-form-urlencoded";
        when(environment.getProperty(eq("openid.contentType"), anyString())).thenReturn(contentType);
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        String expectedBody = "grant_type=authorization_code&code=code&client_id=123&client_secret=1234&redirect_uri=callback";
        assertEquals(expectedBody, body);
    }

    @Test
    public void oauth2GetScopes() {
        List<String> expectedScopes = Arrays.asList("openid", "email", "profile", GROUPS_NAME, ALFIO_GROUPS_NAME, "given_name", "family_name");
        List<String> actualScopes = authenticationManager.getScopes();
        assertEquals(expectedScopes, actualScopes);
    }

    @Test
    public void oauth2GetScopesOverrideClaims() {
        var familyNameClaim = "my-family-name";
        var givenNameClaim = "my-given-name";
        when(environment.getProperty(eq("openid.familyNameClaim"))).thenReturn(familyNameClaim);
        when(environment.getProperty(eq("openid.givenNameClaim"))).thenReturn(givenNameClaim);
        List<String> expectedScopes = Arrays.asList("openid", "email", "profile", GROUPS_NAME, ALFIO_GROUPS_NAME, givenNameClaim, familyNameClaim);
        List<String> actualScopes = authenticationManager.getScopes();
        assertEquals(expectedScopes, actualScopes);
    }
}