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

import alfio.repository.user.OrganizationRepository;
import lombok.extern.log4j.Log4j2;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Log4j2
public class RowLevelSecurity {

    private static final OrRequestMatcher IS_PUBLIC_URLS = new OrRequestMatcher(
        new AntPathRequestMatcher("/resources/**"),
        new AntPathRequestMatcher("/event/**"),
        new AntPathRequestMatcher("/"),
        new AntPathRequestMatcher("/file/**"),
        new AntPathRequestMatcher("/api/events/**"),
        new AntPathRequestMatcher("/api/webhook/**"),
        new AntPathRequestMatcher("/api/payment/**"),
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

    private static boolean isAdmin() {
        if(isLoggedUser()) {
            return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        }
        return false;
    }

    @Aspect
    public static class RoleAndOrganizationsAspect {
        private final NamedParameterJdbcTemplate jdbcTemplate;

        private final OrganizationRepository organizationRepository;
        public RoleAndOrganizationsAspect(NamedParameterJdbcTemplate namedParameterJdbcTemplate,
                                          OrganizationRepository organizationRepository) {
            this.jdbcTemplate = namedParameterJdbcTemplate;
            this.organizationRepository = organizationRepository;
        }

        @Around("within(alfio.manager..*) && (@target(org.springframework.transaction.annotation.Transactional) || " +
            " @annotation(org.springframework.transaction.annotation.Transactional))")
        public Object setRoleAndVariable(ProceedingJoinPoint joinPoint) throws Throwable {

            boolean mustCheck;

            if (isInAHttpRequest()) {
                HttpServletRequest request = Objects.requireNonNull((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
                DataSource dataSource = Objects.requireNonNull(jdbcTemplate.getJdbcTemplate().getDataSource());
                Connection connection = null;

                try {
                    String formattedOrgIds = "";
                    mustCheck = !isCurrentlyInAPublicUrlRequest() && isLoggedUser() && !isAdmin();
                    if (mustCheck) {
                        connection = DataSourceUtils.getConnection(dataSource);
                        if (DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                            jdbcTemplate.update("reset alfio.checkRowAccess", new EmptySqlParameterSource());
                            jdbcTemplate.update("reset alfio.currentUserOrgs", new EmptySqlParameterSource());
                            //System.err.println(request.getRequestURL()+" - "+joinPoint+" LOCAL VALUES ARE: alfio.checkRowAccess: " + jdbcTemplate.queryForObject("select current_setting('alfio.checkRowAccess', true)", new EmptySqlParameterSource(), String.class));
                            //System.err.println(request.getRequestURL()+" - "+joinPoint+" LOCAL VALUES ARE: alfio.currentUserOrgs: " + jdbcTemplate.queryForObject("select current_setting('alfio.currentUserOrgs', true)", new EmptySqlParameterSource(), String.class));
                            //System.err.println(request.getRequestURL()+" - "+joinPoint+" result from function call is:" + jdbcTemplate.queryForObject("select alfio_check_row_access(null)", new EmptySqlParameterSource(), Boolean.class));
                            Set<Integer> orgIds = new TreeSet<>(organizationRepository.findAllOrganizationIdForUser(SecurityContextHolder.getContext().getAuthentication().getName()));
                            //System.err.println(joinPoint+" org ids are " + orgIds);
                            if (orgIds.isEmpty()) {
                                log.warn("orgIds is empty, was not able to apply currentUserOrgs at join point: {}", joinPoint);
                            } else {
                                formattedOrgIds = orgIds.stream().map(s -> Integer.toString(s)).collect(Collectors.joining(","));
                                jdbcTemplate.update("set local alfio.checkRowAccess = true", new EmptySqlParameterSource());
                                //cannot use bind variable when calling set local, it's ugly :(
                                jdbcTemplate.update("set local alfio.currentUserOrgs = '" + formattedOrgIds + "'", new EmptySqlParameterSource());
                            }
                        }
                        // note, the policy will check if the variable alfio.checkRowAccess is present before doing anything
                        if (log.isTraceEnabled()) {
                            logEntry(connection, dataSource, joinPoint, request, mustCheck, formattedOrgIds);
                        }
                    }
                } finally {
                    if (connection != null) {
                        DataSourceUtils.releaseConnection(connection, dataSource);
                    }
                }
            }
            return joinPoint.proceed();
        }




        private static void logEntry(Connection connection, DataSource dataSource,
                                           ProceedingJoinPoint joinPoint,
                                           HttpServletRequest request,
                                           boolean mustCheck,
                                           String formattedOrgIds) {

            StringBuilder sb = new StringBuilder();


            if (connection != null && DataSourceUtils.isConnectionTransactional(connection, dataSource)) {
                sb.append("-----------\n");
                sb.append("connection is transactional\n");
                sb.append("URL IS ").append(request.getRequestURI()).append("\n");
                sb.append(request.getRequestURL()).append("\n");
                sb.append(joinPoint).append("\n");
                sb.append("public url: ").append(isCurrentlyInAPublicUrlRequest()).append("\n");

                if (SecurityContextHolder.getContext() != null && SecurityContextHolder.getContext().getAuthentication() != null) {
                    sb.append("auth is ").append(SecurityContextHolder.getContext().getAuthentication().getName()).append("\n");
                } else {
                    sb.append("no user\n");
                }

                sb.append("must check row access: ").append(mustCheck).append("\n");

                if(mustCheck) {
                    sb.append("org ids are: ").append(formattedOrgIds).append("\n");
                }
                sb.append("-----------\n");

            } else {
                sb.append("-----------\n");
                sb.append("connection is NOT transactional so the check will not be done!\n");
                sb.append("URL IS ").append(request.getRequestURI()).append("\n");
                sb.append(joinPoint).append("\n");
                sb.append("-----------\n");
            }

            log.trace(sb.toString());
        }
    }
}
