package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Profile("google")
public class GoogleAuthenticationManager implements OpenIdAuthenticationManager
{
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String claimsUrl;

    public GoogleAuthenticationManager(@Value("${google.oauth2.domain}") String domain,
                                       @Value("${google.oauth2.clientId}") String clientId,
                                       @Value("${google.oauth2.clientSecret}") String clientSecret,
                                       @Value("${google.oauth2.callbackURI}") String callbackURI,
                                       @Value("${google.oauth2.claimsUrl}") String claimsUrl){

        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.claimsUrl = claimsUrl;
    }

    @Override
    public String buildAuthorizeUrl(List<String> scopes)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(domain);
        builder.append("/o/oauth2/v2/auth?redirect_uri=");
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

    @Override
    public String buildClaimsRetrieverUrl()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(domain);
        builder.append(claimsUrl);
        return builder.toString();
    }

    @Override
    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException
    {
        Map<String, String> body = new HashMap<String, String>() {{
            put("code", code);
            put("grant_type", "authorization_code");
            put("client_id", clientId);
            put("client_secret", clientSecret);
            put("redirect_uri", callbackURI);
        }};

        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.writeValueAsString(body);
    }

    @Override
    public String getCodeNameParameter()
    {
        return "code";
    }

    @Override
    public String getAccessTokenNameParameter()
    {
        return "access_token";
    }

    @Override
    public String getIdTokenNameParameter()
    {
        return "id_token";
    }

    @Override
    public String getSubjectNameParameter()
    {
        return "sub";
    }
}
