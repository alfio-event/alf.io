package alfio.manager;

import java.util.List;

public interface OpenIdAuthenticationManager
{
    String buildAuthorizeUrl(List<String> scopes);
    String buildClaimsRetrieverUrl();
}
