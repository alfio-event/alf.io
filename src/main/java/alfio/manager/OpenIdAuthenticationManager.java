package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Profile("oauth2")
public class OpenIdAuthenticationManager
{
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String authenticationUrl;
    private final String claimsUrl;
    private final String contentType;
    private final String groupsNameParameter;
    private final String alfioGroupsNameParameter;
    private final String logoutUrl;
    private final String logoutRedirectUrl;

    public OpenIdAuthenticationManager(@Value("${oauth2.domain}") String domain,
                                       @Value("${oauth2.clientId}") String clientId,
                                       @Value("${oauth2.clientSecret}") String clientSecret,
                                       @Value("${oauth2.callbackURI}") String callbackURI,
                                       @Value("${oauth2.authenticationUrl}") String authenticationUrl,
                                       @Value("${oauth2.claimsUrl}") String claimsUrl,
                                       @Value("${oauth2.contentType}") String contentType,
                                       @Value("${oauth2.groupsNameParameter}") String groupsNameParameter,
                                       @Value("${oauth2.alfioGroupsNameParameter}") String alfioGroupsNameParameter,
                                       @Value("${oauth2.logoutUrl}") String logoutUrl,
                                       @Value("${oauth2.logoutRedirectUrl}") String logoutRedirectUrl){
        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.authenticationUrl = authenticationUrl;
        this.claimsUrl = claimsUrl;
        this.contentType = contentType;
        this.groupsNameParameter = groupsNameParameter;
        this.alfioGroupsNameParameter = alfioGroupsNameParameter;
        this.logoutUrl = logoutUrl;
        this.logoutRedirectUrl = logoutRedirectUrl;
    }

    public String buildAuthorizeUrl(List<String> scopes){
        String scopeParameter = scopes.stream().collect(Collectors.joining(" "));
        System.out.println(scopeParameter);

        URIBuilder builder = new URIBuilder()
            .setScheme("https")
            .setHost(domain)
            .setPath(authenticationUrl)
            .addParameter("redirect_uri", callbackURI)
            .addParameter("client_id", clientId)
            .addParameter("scope", scopeParameter)
            .addParameter("response_type", "code");
        return builder.toString();
    }

    public String buildClaimsRetrieverUrl()
    {
        URIBuilder builder = new URIBuilder()
            .setScheme("https")
            .setHost(domain)
            .setPath(claimsUrl);
        return builder.toString();
    }

    public String buildLogoutUrl()
    {
        URIBuilder builder = new URIBuilder()
            .setScheme("https")
            .setHost(domain)
            .setPath(logoutUrl)
            .addParameter("redirect_uri", logoutRedirectUrl);
        return builder.toString();
    }

    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException
    {
        if(contentType.equals("application/json"))
            return buildRetrieveClaimsUrlJsonBody(code);
        if(contentType.equals("application/x-www-form-urlencoded"))
            return buildRetrieveClaimsUrlFormUrlEncodedBody(code);
        throw new RuntimeException("the Content-Type you specifier is not supported");
    }

    private String buildRetrieveClaimsUrlJsonBody(String code) throws JsonProcessingException
    {
        Map<String, String> body = new HashMap<String, String>() {{
            put("grant_type", "authorization_code");
            put("code", code);
            put("client_id", clientId);
            put("client_secret", clientSecret);
            put("redirect_uri", callbackURI);
        }};

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(body);
    }

    private String buildRetrieveClaimsUrlFormUrlEncodedBody(String code)
    {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("grant_type=authorization_code");
        stringBuilder.append("&code=" + code);
        stringBuilder.append("&client_id=" + clientId);
        stringBuilder.append("&client_secret=" + clientSecret);
        stringBuilder.append("&redirect_uri=" + callbackURI);

        return stringBuilder.toString();
    }

    public List<String> getScopes()
    {
        return Arrays.asList("openid", "email", "profile", groupsNameParameter, alfioGroupsNameParameter);
    }

    public String getGroupsNameParameter()
    {
        return groupsNameParameter;
    }

    public String getAlfioGroupsNameParameter()
    {
        return alfioGroupsNameParameter;
    }

    public String getCodeNameParameter()
    {
        return "code";
    }

    public String getAccessTokenNameParameter()
    {
        return "access_token";
    }

    public String getIdTokenNameParameter()
    {
        return "id_token";
    }

    public String getSubjectNameParameter()
    {
        return "sub";
    }

    public String getEmailNameParameter()
    {
        return "email";
    }

    public String getContentType()
    {
        return contentType;
    }
}

