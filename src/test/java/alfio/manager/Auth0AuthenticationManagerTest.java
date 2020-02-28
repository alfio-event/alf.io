package alfio.manager;

import alfio.controller.IndexController;
import org.junit.*;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Profile("auth0")
public class Auth0AuthenticationManagerTest
{
    private final String DOMAIN = "domain_test";
    private final String CLIENT_ID = "123";
    private final String CLIENT_SECRET = "1234";
    private final String CALLBACK_URI = "callback";
    private final String CLAIMS_URI = "claims";

    private final Auth0AuthenticationManager authenticationManager = new Auth0AuthenticationManager(DOMAIN, CLIENT_ID, CLIENT_SECRET, CALLBACK_URI, CLAIMS_URI);

    @Test
    public void authentication_auth0_test() throws IOException
    {
        List<String> scopes = Arrays.asList("scope1", "scope2");
        String redirectURL = authenticationManager.buildAuthorizeUrl(scopes);
        String expectedURL = "https://domain_test/authorize?redirect_uri=callback&client_id=123&scope=scope1%20scope2&response_type=code";
        Assert.assertEquals(expectedURL, redirectURL);
    }

}