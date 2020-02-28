package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.*;

import java.util.*;

public class Auth0AuthenticationManagerTest
{
    private final String DOMAIN = "domain_test";
    private final String CLIENT_ID = "123";
    private final String CLIENT_SECRET = "1234";
    private final String CALLBACK_URI = "callback";
    private final String CLAIMS_URI = "/claims";

    private final Auth0AuthenticationManager authenticationManager = new Auth0AuthenticationManager(DOMAIN, CLIENT_ID, CLIENT_SECRET, CALLBACK_URI, CLAIMS_URI);

    @Test
    public void auth0_authorize_url_test()
    {
        List<String> scopes = Arrays.asList("scope1", "scope2");
        String redirectURL = authenticationManager.buildAuthorizeUrl(scopes);
        String expectedURL = "https://domain_test/authorize?redirect_uri=callback&client_id=123&scope=scope1%20scope2&response_type=code";
        Assert.assertEquals(expectedURL, redirectURL);
    }

    @Test
    public void auth0_claims_url_test()
    {
        String claimsUrl = authenticationManager.buildClaimsRetrieverUrl();
        String expectedURL = "https://domain_test/claims";
        Assert.assertEquals(expectedURL, claimsUrl);
    }

    @Test
    public void auth0_build_body_test() throws JsonProcessingException
    {
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        String expectedBody = "{\"code\":\"code\",\"grant_type\":\"authorization_code\",\"client_secret\":\"1234\",\"redirect_uri\":\"callback\",\"client_id\":\"123\"}";
        Assert.assertEquals(expectedBody, body);
    }

}