package alfio.manager;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import org.junit.*;

    import java.util.*;

public class OpenIdAuthenticationManagerTest
{
    private final String DOMAIN = "domain_test";
    private final String CLIENT_ID = "123";
    private final String CLIENT_SECRET = "1234";
    private final String CALLBACK_URI = "callback";
    private final String AUTHENTICATION_URL = "/auth";
    private final String CLAIMS_URI = "/claims";
    private final String CONTENT_TYPE = "application/json";

    private final OpenIdAuthenticationManager authenticationManager = new OpenIdAuthenticationManager(DOMAIN, CLIENT_ID, CLIENT_SECRET, CALLBACK_URI, AUTHENTICATION_URL, CLAIMS_URI, CONTENT_TYPE);

    @Test
    public void oauth2_authorize_url_test()
    {
        List<String> scopes = Arrays.asList("scope1", "scope2");
        String redirectURL = authenticationManager.buildAuthorizeUrl(scopes);
        String expectedURL = "https://domain_test/auth?redirect_uri=callback&client_id=123&scope=scope1%20scope2&response_type=code";
        Assert.assertEquals(expectedURL, redirectURL);
    }

    @Test
    public void oauth2_claims_url_test()
    {
        String claimsUrl = authenticationManager.buildClaimsRetrieverUrl();
        String expectedURL = "https://domain_test/claims";
        Assert.assertEquals(expectedURL, claimsUrl);
    }

    @Test
    public void oauth2_json_build_body_test() throws JsonProcessingException
    {
        String code = "code";
        String body = authenticationManager.buildRetrieveClaimsUrlBody(code);
        String expectedBody = "{\"code\":\"code\",\"grant_type\":\"authorization_code\",\"client_secret\":\"1234\",\"redirect_uri\":\"callback\",\"client_id\":\"123\"}";
        Assert.assertEquals(expectedBody, body);
    }

    @Test
    public void oauth2_form_url_encoded_build_body_test() throws JsonProcessingException
    {
        String contentType = "application/x-www-form-urlencoded";
        OpenIdAuthenticationManager authenticationManagerUrlEncoded = new OpenIdAuthenticationManager(DOMAIN, CLIENT_ID, CLIENT_SECRET, CALLBACK_URI, AUTHENTICATION_URL, CLAIMS_URI, contentType);
        String code = "code";
        String body = authenticationManagerUrlEncoded.buildRetrieveClaimsUrlBody(code);
        String expectedBody = "grant_type=authorization_code&code=code&client_id=123&client_secret=1234&redirect_uri=callback";
        Assert.assertEquals(expectedBody, body);
    }

    @Test
    public void oauth2_parameters_name_test(){
        Assert.assertEquals("code", authenticationManager.getCodeNameParameter());
        Assert.assertEquals("access_token", authenticationManager.getAccessTokenNameParameter());
        Assert.assertEquals("id_token", authenticationManager.getIdTokenNameParameter());
        Assert.assertEquals("sub", authenticationManager.getSubjectNameParameter());
    }

}