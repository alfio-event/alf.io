/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.config.authentication.support;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.io.Serial;
import java.util.Collection;
import java.util.Objects;

public class OpenIdPrincipal extends DefaultOidcUser {

    @Serial
    private static final long serialVersionUID = -3305276997530613807L;
    private final OpenIdAlfioUser alfioUser;
    private final String idpLogoutRedirectionUrl;

    public OpenIdPrincipal(Collection<? extends GrantedAuthority> authorities,
                           OidcIdToken idToken,
                           OidcUserInfo userInfo,
                           OpenIdAlfioUser alfioUser,
                           String idpLogoutRedirectionUrl) {
        super(authorities, idToken, userInfo);
        this.alfioUser = alfioUser;
        this.idpLogoutRedirectionUrl = idpLogoutRedirectionUrl;
    }

    public OpenIdAlfioUser user() {
        return alfioUser;
    }

    public String idpLogoutRedirectionUrl() {
        return idpLogoutRedirectionUrl;
    }

    @Override
    public String getName() {
        return user().email();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        OpenIdPrincipal that = (OpenIdPrincipal) o;
        return Objects.equals(alfioUser, that.alfioUser) && Objects.equals(idpLogoutRedirectionUrl, that.idpLogoutRedirectionUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), alfioUser, idpLogoutRedirectionUrl);
    }
}
