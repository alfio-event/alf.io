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
    private final String GROUPS_NAME = "groups";
    private final String ALFIO_GROUPS_NAME = "alfio-groups";
    private final String LOGOUT_URL = "/logoutUrl";
    private final String LOGOUT_REDIRECT_URL = "logoutRedirectUrl";

    private final OpenIdAuthenticationManager authenticationManager = new OpenIdAuthenticationManager(DOMAIN, CLIENT_ID,
        CLIENT_SECRET, CALLBACK_URI, AUTHENTICATION_URL, CLAIMS_URI, CONTENT_TYPE, GROUPS_NAME, ALFIO_GROUPS_NAME,
        LOGOUT_URL, LOGOUT_REDIRECT_URL);

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
        OpenIdAuthenticationManager authenticationManagerUrlEncoded = new OpenIdAuthenticationManager(DOMAIN, CLIENT_ID, CLIENT_SECRET, CALLBACK_URI,
            AUTHENTICATION_URL, CLAIMS_URI, contentType, GROUPS_NAME, ALFIO_GROUPS_NAME,
            LOGOUT_URL, LOGOUT_REDIRECT_URL);
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
        Assert.assertEquals("email", authenticationManager.getEmailNameParameter());
        Assert.assertEquals(CONTENT_TYPE, authenticationManager.getContentType());
        Assert.assertEquals("sub", authenticationManager.getSubjectNameParameter());
        Assert.assertEquals(GROUPS_NAME, authenticationManager.getGroupsNameParameter());
        Assert.assertEquals(ALFIO_GROUPS_NAME, authenticationManager.getAlfioGroupsNameParameter());
        Assert.assertEquals(LOGOUT_REDIRECT_URL, authenticationManager.getLogoutRedirectUrl());
    }

    @Test
    public void oauth2_get_scopes(){
        List<String> expectedScopes = Arrays.asList("openid", "email", "profile", GROUPS_NAME, ALFIO_GROUPS_NAME);
        List<String> actualScopes = authenticationManager.getScopes();
        Assert.assertEquals(expectedScopes, actualScopes);
    }

    @Test
    public void oauth2_build_logoutUrl(){
        String logoutUrl = authenticationManager.buildLogoutUrl();
        String expectedURL = "https://domain_test/logoutUrl";
        Assert.assertEquals(expectedURL, logoutUrl);
    }

}