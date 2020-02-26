package alfio.controller;

import org.junit.*;

import java.io.IOException;
import java.util.*;

public class IndexControllerTest
{
    @Test
    public void authentication_auth0_test() throws IOException
    {
        String domain = "domain_test";
        String redirectURI = "redirect_test";
        String clientId = "123";
        List<String> scopes = Arrays.asList("scope1", "scope2");
        String redirectURL = IndexController.createAuth0RedirectionURL(domain, redirectURI, clientId, scopes);
        String expectedURL = "https://domain_test/authorize?redirect_uri=redirect_test&client_id=123&scope=scope1%20scope2&response_type=code";
        Assert.assertEquals(expectedURL, redirectURL);
    }

}