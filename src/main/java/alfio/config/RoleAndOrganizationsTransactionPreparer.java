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
package alfio.config;

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static alfio.config.authentication.support.AuthenticationConstants.ADMIN;
import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;

@Log4j2
class RoleAndOrganizationsTransactionPreparer {

    private RoleAndOrganizationsTransactionPreparer() {}

    private static final OrRequestMatcher IS_PUBLIC_URLS = new OrRequestMatcher(
        new AntPathRequestMatcher("/resources/**"),
        new AntPathRequestMatcher("/webjars/**"),
        new AntPathRequestMatcher("/event/**"),
        new AntPathRequestMatcher("/"),
        new AntPathRequestMatcher("/file/**"),
        new AntPathRequestMatcher("/api/events/**"),
        new AntPathRequestMatcher("/api/webhook/**"),
        new AntPathRequestMatcher("/api/payment/**"),
        new AntPathRequestMatcher("/api/pass/**"),
        new AntPathRequestMatcher("/api/v2/info"),
        new AntPathRequestMatcher("/api/v2/public/**"),
        new AntPathRequestMatcher("/session-expired"),
        new AntPathRequestMatcher("/authentication"));

    private static boolean isCurrentlyInAPublicUrlRequest() {
        HttpServletRequest request = Objects.requireNonNull((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return IS_PUBLIC_URLS.matches(request);
    }

    private static boolean isInAHttpRequest() {
        return RequestContextHolder.getRequestAttributes() != null;
    }

    private static boolean isLoggedUser() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() != null) {
            return !"anonymousUser".equals(context.getAuthentication().getName());
        }
        return false;
    }

    private static boolean isPublic() {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context != null && context.getAuthentication() instanceof OpenIdAlfioAuthentication) {
            return ((OpenIdAlfioAuthentication) context.getAuthentication()).isPublicUser();
        }
        return false;
    }

    private static boolean isAdmin() {
        if(isLoggedUser()) {
            return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_" + SYSTEM_API_CLIENT) || authority.equals("ROLE_" + ADMIN));
        }
        return false;
    }

    private static final String QUERY_ORG_FOR_USER = "(select organization.id from organization inner join j_user_organization on org_id = organization.id where j_user_organization.user_id = (select ba_user.id from ba_user where ba_user.username = ?)) " +
        " union " +
        "(select organization.id from organization where 'ROLE_ADMIN' in (select role from ba_user inner join authority on ba_user.username = authority.username where ba_user.username = ?))";

    public static void prepareTransactionalConnection(Connection connection) throws SQLException {
        if (!isInAHttpRequest()) {
            return;
        }
        boolean mustCheck = !isCurrentlyInAPublicUrlRequest() && isLoggedUser() && !isPublic() && !isAdmin();
        if (!mustCheck) {
            return;
        }

        try (var s = connection.createStatement()) {
            s.execute("reset alfio.checkRowAccess");
        }
        try (var s = connection.createStatement()) {
            s.execute("reset alfio.currentUserOrgs");
        }

        Set<Integer> orgIds = new TreeSet<>();
        try (var s = connection.prepareStatement(QUERY_ORG_FOR_USER)) {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            s.setString(1, username);
            s.setString(2, username);
            try (var rs = s.executeQuery()) {
                while (rs.next()) {
                    orgIds.add(rs.getInt(1));
                }
            }
        }

        if (orgIds.isEmpty()) {
            log.warn("orgIds is empty, was not able to apply currentUserOrgs");
        } else {
            try (var s = connection.createStatement()) {
                s.execute("set local alfio.checkRowAccess = true");
            }
            try (var s = connection.createStatement()) {
                String formattedOrgIds = orgIds.stream().map(orgId -> Integer.toString(orgId)).collect(Collectors.joining(","));
                //can't use placeholder in a prepared statement when using 'set ...', but cannot be sql injected as the source is a set of integers
                s.execute("set local alfio.currentUserOrgs = '" + formattedOrgIds + "'");
            }
        }
    }
}
