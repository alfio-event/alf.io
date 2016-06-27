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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.util.regex.Pattern;

@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    public static final String ADMIN_API = "/admin/api";
    public static final String CSRF_SESSION_ATTRIBUTE = "CSRF_SESSION_ATTRIBUTE";
    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String OPERATOR = "OPERATOR";
    public static final String SPONSOR = "SPONSOR";
    private static final String ADMIN = "ADMIN";
    private static final String OWNER = "OWNER";



    private static class BaseWebSecurity extends  WebSecurityConfigurerAdapter {

        @Autowired
        private DataSource dataSource;
        @Autowired
        private PasswordEncoder passwordEncoder;

        @Override
        public void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.jdbcAuthentication().dataSource(dataSource)
                    .usersByUsernameQuery("select username, password, enabled from ba_user where username = ?")
                    .authoritiesByUsernameQuery("select username, role from authority where username = ?")
                    .passwordEncoder(passwordEncoder);
        }
    }

    /**
     * Basic auth configuration for Mobile App.
     * The rules are only valid if the header Authorization is present, otherwise it fallback to the
     * FormBasedWebSecurity rules.
     */
    @Configuration
    @Order(1)
    public static class BasicAuthWebSecurity extends BaseWebSecurity {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.requestMatcher((request) -> request.getHeader("Authorization") != null).sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and().csrf().disable()
            .authorizeRequests()
            .antMatchers(ADMIN_API + "/check-in/**").hasRole(OPERATOR)
            .antMatchers(HttpMethod.GET, ADMIN_API + "/events").hasAnyRole(OPERATOR, SPONSOR)
            .antMatchers(ADMIN_API + "/user-type").hasAnyRole(OPERATOR, SPONSOR)
            .antMatchers(ADMIN_API + "/**").denyAll()
            .antMatchers(HttpMethod.POST, "/api/attendees/sponsor-scan").hasRole(SPONSOR)
            .antMatchers("/**").authenticated()
            .and().httpBasic();
        }
    }

    /**
     * Default form based configuration.
     */
    @Configuration
    @Order(2)
    public static class FormBasedWebSecurity extends BaseWebSecurity {

        @Autowired
        private Environment environment;

        @Bean
        public CsrfTokenRepository getCsrfTokenRepository() {
            HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
            repository.setSessionAttributeName(CSRF_SESSION_ATTRIBUTE);
            repository.setParameterName(CSRF_PARAM_NAME);
            return repository;
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            if(environment.acceptsProfiles("!"+Initializer.PROFILE_DEV)) {
                http.requiresChannel().anyRequest().requiresSecure();
            }

            CsrfConfigurer<HttpSecurity> configurer =
                http.exceptionHandling()
                    .accessDeniedPage("/session-expired")
                    .defaultAuthenticationEntryPointFor((request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED), new RequestHeaderRequestMatcher("X-Requested-With", "XMLHttpRequest"))
                    .and()
                    .headers().cacheControl().disable()
                    .and()
                    .csrf();
            if(environment.acceptsProfiles(Initializer.PROFILE_DEBUG_CSP)) {
                Pattern whiteList = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");
                configurer.requireCsrfProtectionMatcher(new NegatedRequestMatcher((r) -> whiteList.matcher(r.getMethod()).matches() || r.getRequestURI().equals("/report-csp-violation")));
            }
            configurer.csrfTokenRepository(getCsrfTokenRepository())
                .and()
                .authorizeRequests()
                .antMatchers(ADMIN_API + "/configuration/**", ADMIN_API + "/users/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers(ADMIN_API + "/organizations/new").hasRole(ADMIN)
                .antMatchers(ADMIN_API + "/check-in/**").hasAnyRole(ADMIN, OWNER, OPERATOR)
                .antMatchers(HttpMethod.GET, ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER, OPERATOR)
                .antMatchers(ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers("/admin/**/export/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers("/admin/**").hasAnyRole(ADMIN, OWNER, OPERATOR)
                .antMatchers("/api/attendees/**").denyAll()
                .antMatchers("/**").permitAll()
                .and()
                .formLogin()
                .loginPage("/authentication")
                .loginProcessingUrl("/authenticate")
                .failureUrl("/authentication?failed")
                .and().logout().permitAll();
        }
    }




}
