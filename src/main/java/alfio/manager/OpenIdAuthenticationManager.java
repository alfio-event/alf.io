package alfio.manager;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import org.springframework.beans.factory.annotation.*;
    import org.springframework.context.annotation.Profile;
    import org.springframework.stereotype.Component;

    import java.net.URI;
    import java.net.http.HttpRequest;
    import java.util.*;

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
    private String contentType;

    public OpenIdAuthenticationManager(@Value("${oauth2.domain}") String domain,
                                      @Value("${oauth2.clientId}") String clientId,
                                      @Value("${oauth2.clientSecret}") String clientSecret,
                                      @Value("${oauth2.callbackURI}") String callbackURI,
                                      @Value("${oauth2.authenticationUrl}") String authenticationUrl,
                                      @Value("${oauth2.claimsUrl}") String claimsUrl,
                                      @Value("${oauth2.contentType}") String contentType){

        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.authenticationUrl = authenticationUrl;
        this.claimsUrl = claimsUrl;
        this.contentType = contentType;
    }

    public String buildAuthorizeUrl(List<String> scopes){
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(domain);
        builder.append(authenticationUrl);
        builder.append("?redirect_uri=");
        builder.append(callbackURI);
        builder.append("&client_id=");
        builder.append(clientId);
        builder.append("&scope=");

        for(int i = 0; i < scopes.size(); i++){
            if(i != 0)
                builder.append("%20");
            builder.append(scopes.get(i));
        }

        builder.append("&response_type=code");
        return builder.toString();
    }

    public String buildClaimsRetrieverUrl()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(domain);
        builder.append(claimsUrl);
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

