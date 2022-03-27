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

import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Role;
import alfio.model.user.User;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;

// generate a user if it does not exist, to be used by the demo profile
public class UserCreatorBeforeLoginFilter extends GenericFilterBean {

    private final UserManager userManager;
    private final RequestMatcher requestMatcher;

    public UserCreatorBeforeLoginFilter(UserManager userManager, String loginProcessingUrl) {
        this.userManager = userManager;
        this.requestMatcher = new AntPathRequestMatcher(loginProcessingUrl, "POST");
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;

        //ensure organization/user
        if (requestMatcher.matches(req) && req.getParameter("username") != null && req.getParameter("password") != null) {
            String username = req.getParameter("username");
            if (!userManager.usernameExists(username)) {
                var organizationModification = new OrganizationModification(null, UUID.randomUUID().toString(), username, username, null, null);
                int orgId = userManager.createOrganization(organizationModification);
                userManager.insertUser(orgId, username, "", "", username, Role.OWNER, User.Type.DEMO, req.getParameter("password"), null, null);
            }
        }

        chain.doFilter(request, response);
    }
}
