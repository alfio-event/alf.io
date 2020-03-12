package alfio.manager;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;

public interface OpenIdAuthenticationManager
{
    String buildAuthorizeUrl(List<String> scopes);
    String buildClaimsRetrieverUrl();
    String buildRetrieveClaimsUrlBody(String code) throws JsonProcessingException;

    String getCodeNameParameter();
    String getAccessTokenNameParameter();
    String getIdTokenNameParameter();
    String getSubjectNameParameter();
    String getEmailNameParameter();
}
