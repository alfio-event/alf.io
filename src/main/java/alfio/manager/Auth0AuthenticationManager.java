package alfio.manager;

import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("auth0")
public class Auth0AuthenticationManager implements OpenIdAuthenticationManager
{
    private final String domain;
    private final String clientId;
    private final String clientSecret;
    private final String callbackURI;
    private final String claimsUrl;

    public Auth0AuthenticationManager(@Value("${auth0.domain}") String domain,
                                      @Value("${auth0.clientId}") String clientId,
                                      @Value("${auth0.clientSecret}") String clientSecret,
                                      @Value("${auth0.callbackURI}") String callbackURI,
                                      @Value("${auth0.claimsUrl}") String claimsUrl){

        this.domain = domain;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackURI = callbackURI;
        this.claimsUrl = claimsUrl;
    }

    @Override
    public String buildAuthorizeUrl(List<String> scopes){
        StringBuilder builder = new StringBuilder();
        builder.append("https://");
        builder.append(domain);
        builder.append("/authorize?redirect_uri=");
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
}
