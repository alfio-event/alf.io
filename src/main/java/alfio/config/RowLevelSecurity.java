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

import alfio.repository.GroupRepository;
import alfio.repository.user.UserRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.sql.Connection;

public class RowLevelSecurity {


    private static final String IS_PUBLIC_URL_KEY = "alfio.config.RowLevelSecurity.IS_PUBLIC_API";

    private static final OrRequestMatcher IS_PUBLIC_URLS = new OrRequestMatcher(
        new AntPathRequestMatcher("/"),
        new AntPathRequestMatcher("/session-expired"),
        new AntPathRequestMatcher("/authentication"),
        new AntPathRequestMatcher("/resources/**"),
        new AntPathRequestMatcher("/event/**"),
        new AntPathRequestMatcher("/file/**"),
        new AntPathRequestMatcher("/api/events/**"));


    public static class PublicApiMarkingHandlerInterceptor extends HandlerInterceptorAdapter {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (IS_PUBLIC_URLS.matches(request)) {
                request.setAttribute(IS_PUBLIC_URL_KEY, Boolean.TRUE);
            }
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
            request.removeAttribute(IS_PUBLIC_URL_KEY);
        }
    }

    private static boolean isCurrentlyInAPublicUrlRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        return requestAttributes != null && Boolean.TRUE == requestAttributes.getAttribute(IS_PUBLIC_URL_KEY, RequestAttributes.SCOPE_REQUEST);
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

    @Aspect
    public static class RoleAndOrganizationsAspect {
        private final NamedParameterJdbcTemplate jdbcTemplate;

        private final GroupRepository groupRepository;
        private final UserRepository userRepository;

        public RoleAndOrganizationsAspect(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                          GroupRepository groupRepository,
                                          UserRepository userRepository) {
            this.jdbcTemplate = namedParameterJdbcTemplate;
            this.groupRepository = groupRepository;
            this.userRepository = userRepository;
        }


        @Around("within(alfio.manager..*) && (@target(org.springframework.transaction.annotation.Transactional) || " +
            " @annotation(org.springframework.transaction.annotation.Transactional))")
        public Object setRoleAndVariable(ProceedingJoinPoint joinPoint) throws Throwable {


            if(isInAHttpRequest()) {

                HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                System.err.println("URL IS " + request.getRequestURI());

                //TODO: if is in a public api:
                // set alfio.public_api = 'true'
                // don't change role

                // if it's not a public api
                // change role, fetch group of user (or set a variable if admin)
                //

                System.err.println(joinPoint);
                System.err.println("public url: " + isCurrentlyInAPublicUrlRequest());

                if(SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
                    System.err.println("auth is "+SecurityContextHolder.getContext().getAuthentication().getName());
                } else {
                    System.err.println("no user");
                }


                DataSource dataSource = jdbcTemplate.getJdbcTemplate().getDataSource();
                Connection connection = DataSourceUtils.getConnection(dataSource);
                if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                    System.err.println("connection is transactional");
                    jdbcTemplate.update("set local role application_user", new EmptySqlParameterSource());
                } else {
                    System.err.println("connection is not transactional");
                }
            }

            return joinPoint.proceed();
        }

    }
}
