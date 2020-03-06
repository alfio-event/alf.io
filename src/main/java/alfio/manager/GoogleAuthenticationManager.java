package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("google")
public class GoogleAuthenticationManager implements OpenIdAuthenticationManager
{
    @Override
    public String buildAuthorizeUrl(List<String> scopes)
    {
        return null;
    }

    @Override
    public String buildClaimsRetrieverUrl()
    {
        return null;
    }

    @Override
    public String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException
    {
        return null;
    }

    @Override
    public String getCodeNameParameter()
    {
        return null;
    }

    @Override
    public String getAccessTokenNameParameter()
    {
        return null;
    }

    @Override
    public String getIdTokenNameParameter()
    {
        return null;
    }

    @Override
    public String getSubjectNameParameter()
    {
        return null;
    }
}
