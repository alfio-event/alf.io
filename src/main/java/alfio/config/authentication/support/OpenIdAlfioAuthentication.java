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

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.Serializable;
import java.util.Collection;

public class OpenIdAlfioAuthentication extends AbstractAuthenticationToken implements Serializable {
    private final String idToken;
    private final String subject;
    private final String email;
    private final String idpLogoutRedirectionUrl;
    private final boolean publicUser;

    public OpenIdAlfioAuthentication(Collection<? extends GrantedAuthority> authorities,
                                     String idToken,
                                     String subject,
                                     String email,
                                     String idpLogoutRedirectionUrl,
                                     boolean publicUser) {
        super(authorities);
        this.idToken = idToken;
        this.subject = subject;
        this.email = email;
        this.idpLogoutRedirectionUrl = idpLogoutRedirectionUrl;
        this.publicUser = publicUser;
    }

    @Override
    public Object getCredentials() {
        return idToken;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }

    @Override
    public String getName() {
        return email;
    }

    public String getIdpLogoutRedirectionUrl() {
        return idpLogoutRedirectionUrl;
    }

    public boolean isPublicUser() {
        return publicUser;
    }
}
