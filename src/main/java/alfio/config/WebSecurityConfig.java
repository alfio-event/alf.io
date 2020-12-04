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

import alfio.config.support.auth.*;
import alfio.manager.RecaptchaService;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.OpenIdAuthenticationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.ClockProvider;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.time.ZonedDateTime;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@Log4j2
public class WebSecurityConfig {

    public static final String CSRF_PARAM_NAME = "_csrf";
    public static final String OPERATOR = "OPERATOR";
    public static final String SPONSOR = "SPONSOR";
    private static final String ADMIN_API = "/admin/api";
    private static final String ADMIN_PUBLIC_API = "/api/v1/admin";
    private static final String CSRF_SESSION_ATTRIBUTE = "CSRF_SESSION_ATTRIBUTE";
    private static final String SUPERVISOR = "SUPERVISOR";
    private static final String ADMIN = "ADMIN";
    private static final String OWNER = "OWNER";
    private static final String API_CLIENT = "API_CLIENT";
    private static final String X_REQUESTED_WITH = "X-Requested-With";

    @Bean
    public CsrfTokenRepository getCsrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setSessionAttributeName(CSRF_SESSION_ATTRIBUTE);
        repository.setParameterName(CSRF_PARAM_NAME);
        return repository;
    }

    @Configuration
    @Order(0)
    public static class APITokenAuthWebSecurity extends WebSecurityConfigurerAdapter {

        private final UserRepository userRepository;
        private final AuthorityRepository authorityRepository;

        public APITokenAuthWebSecurity(UserRepository userRepository,
                                       AuthorityRepository authorityRepository) {
            this.userRepository = userRepository;
            this.authorityRepository = authorityRepository;
        }

        //https://stackoverflow.com/a/48448901
        @Override
        protected void configure(HttpSecurity http) throws Exception {

            APIKeyAuthFilter filter = new APIKeyAuthFilter();
            filter.setAuthenticationManager(authentication -> {
                //
                String apiKey = (String) authentication.getPrincipal();
                //check if user type ->
                User user = userRepository.findByUsername(apiKey).orElseThrow(() -> new BadCredentialsException("Api key " + apiKey + " don't exists"));
                if (!user.isEnabled()) {
                    throw new DisabledException("Api key " + apiKey + " is disabled");
                }
                if (User.Type.API_KEY != user.getType()) {
                    throw new WrongAccountTypeException("Wrong account type for username " + apiKey);
                }
                if (!user.isCurrentlyValid(ZonedDateTime.now(ClockProvider.clock()))) {
                    throw new DisabledException("Api key " + apiKey + " is expired");
                }

                return new APITokenAuthentication(
                    authentication.getPrincipal(),
                    authentication.getCredentials(),
                    authorityRepository.findRoles(apiKey).stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
            });


            http.requestMatcher(RequestTypeMatchers::isTokenAuthentication)
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and().csrf().disable()
                .authorizeRequests()
                .antMatchers(ADMIN_PUBLIC_API + "/**").hasRole(API_CLIENT)
                .antMatchers(ADMIN_API + "/check-in/**").hasAnyRole(OPERATOR, SUPERVISOR)
                .antMatchers(HttpMethod.GET, ADMIN_API + "/events").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
                .antMatchers(HttpMethod.GET, ADMIN_API + "/user-type", ADMIN_API + "/user/details").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
                .antMatchers(ADMIN_API + "/**").denyAll()
                .antMatchers(HttpMethod.POST, "/api/attendees/sponsor-scan").hasRole(SPONSOR)
                .antMatchers(HttpMethod.GET, "/api/attendees/*/ticket/*").hasAnyRole(OPERATOR, SUPERVISOR, API_CLIENT)
                .antMatchers("/**").authenticated()
                .and().addFilter(filter);
        }
    }

    @Profile("openid")
    @Configuration
    @Order(1)
    public static class OpenIdFormBasedWebSecurity extends AbstractFormBasedWebSecurity {

        private final OpenIdAuthenticationManager openIdAuthenticationManager;
        private final UserRepository userRepository;
        private final AuthorityRepository authorityRepository;
        private final UserOrganizationRepository userOrganizationRepository;
        private final OrganizationRepository organizationRepository;

        public OpenIdFormBasedWebSecurity(Environment environment,
                                          UserManager userManager,
                                          RecaptchaService recaptchaService,
                                          ConfigurationManager configurationManager,
                                          CsrfTokenRepository csrfTokenRepository,
                                          DataSource dataSource,
                                          PasswordEncoder passwordEncoder,
                                          OpenIdAuthenticationManager openIdAuthenticationManager,
                                          UserRepository userRepository,
                                          AuthorityRepository authorityRepository,
                                          UserOrganizationRepository userOrganizationRepository,
                                          OrganizationRepository organizationRepository) {
            super(environment, userManager, recaptchaService, configurationManager, csrfTokenRepository, dataSource, passwordEncoder);
            this.openIdAuthenticationManager = openIdAuthenticationManager;
            this.userRepository = userRepository;
            this.authorityRepository = authorityRepository;
            this.userOrganizationRepository = userOrganizationRepository;
            this.organizationRepository = organizationRepository;
        }

        @Override
        protected void customizeAuthenticationManager(AuthenticationManagerBuilder auth) {
            auth.authenticationProvider(new OpenIdAuthenticationProvider());
        }

        @Override
        protected void addAdditionalFilters(HttpSecurity http) throws Exception {
            var callbackLoginFilter = new OpenIdCallbackLoginFilter(openIdAuthenticationManager,
                new AntPathRequestMatcher("/callback", "GET"),
                authenticationManager(),
                userRepository,
                authorityRepository,
                getPasswordEncoder(),
                getUserManager(),
                userOrganizationRepository,
                organizationRepository);
            http.addFilterBefore(callbackLoginFilter, UsernamePasswordAuthenticationFilter.class);
            log.trace("adding openid filter");
            http.addFilterBefore(new OpenIdAuthenticationFilter("/authentication", openIdAuthenticationManager), RecaptchaLoginFilter.class);
        }
    }

    /**
     * Default form based configuration.
     */
    @Profile("!openid")
    @Configuration
    @Order(1)
    public static class FormBasedWebSecurity extends AbstractFormBasedWebSecurity {
        public FormBasedWebSecurity(Environment environment,
                                    UserManager userManager,
                                    RecaptchaService recaptchaService,
                                    ConfigurationManager configurationManager,
                                    CsrfTokenRepository csrfTokenRepository,
                                    DataSource dataSource,
                                    PasswordEncoder passwordEncoder) {
            super(environment, userManager, recaptchaService, configurationManager, csrfTokenRepository, dataSource, passwordEncoder);
        }
    }

    @AllArgsConstructor
    static abstract class AbstractFormBasedWebSecurity extends WebSecurityConfigurerAdapter {
        private final Environment environment;
        private final UserManager userManager;
        private final RecaptchaService recaptchaService;
        private final ConfigurationManager configurationManager;
        private final CsrfTokenRepository csrfTokenRepository;
        private final DataSource dataSource;
        private final PasswordEncoder passwordEncoder;

        @Override
        public void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.jdbcAuthentication().dataSource(dataSource)
                .usersByUsernameQuery("select username, password, enabled from ba_user where username = ?")
                .authoritiesByUsernameQuery("select username, role from authority where username = ?")
                .passwordEncoder(passwordEncoder);
            // call implementation-specific logic
            customizeAuthenticationManager(auth);
        }

        /**
         * By using this method, implementations can customize the AuthenticationManager configuration
         * @param auth
         */
        protected void customizeAuthenticationManager(AuthenticationManagerBuilder auth) {
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {

            if (environment.acceptsProfiles(Profiles.of("!" + Initializer.PROFILE_DEV))) {
                http.requiresChannel().antMatchers("/healthz").requiresInsecure()
                    .and()
                    .requiresChannel().mvcMatchers("/**").requiresSecure();
            }

            CsrfConfigurer<HttpSecurity> configurer =
                http.exceptionHandling()
                    .accessDeniedHandler((request, response, accessDeniedException) -> {
                        if (!response.isCommitted()) {
                            if ("XMLHttpRequest".equals(request.getHeader(X_REQUESTED_WITH))) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            } else if (!response.isCommitted()) {
                                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                RequestDispatcher dispatcher = request.getRequestDispatcher("/session-expired");
                                dispatcher.forward(request, response);
                            }
                        }
                    })
                    .defaultAuthenticationEntryPointFor((request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED), new RequestHeaderRequestMatcher(X_REQUESTED_WITH, "XMLHttpRequest"))
                    .and()
                    .headers().cacheControl().disable()
                    .and()
                    .csrf();

            Pattern pattern = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");
            Predicate<HttpServletRequest> csrfWhitelistPredicate = r -> r.getRequestURI().startsWith("/api/webhook/")
                || r.getRequestURI().startsWith("/api/payment/webhook/")
                || pattern.matcher(r.getMethod()).matches();
            csrfWhitelistPredicate = csrfWhitelistPredicate.or(r -> r.getRequestURI().equals("/report-csp-violation"));
            configurer.requireCsrfProtectionMatcher(new NegatedRequestMatcher(csrfWhitelistPredicate::test));

            String[] ownershipRequired = new String[]{
                ADMIN_API + "/overridable-template",
                ADMIN_API + "/additional-services",
                ADMIN_API + "/events/*/additional-field",
                ADMIN_API + "/event/*/additional-services/",
                ADMIN_API + "/overridable-template/",
                ADMIN_API + "/events/*/promo-code",
                ADMIN_API + "/reservation/event/*/reservations/list",
                ADMIN_API + "/events/*/email/",
                ADMIN_API + "/event/*/waiting-queue/load",
                ADMIN_API + "/events/*/pending-payments",
                ADMIN_API + "/events/*/export",
                ADMIN_API + "/events/*/sponsor-scan/export",
                ADMIN_API + "/events/*/invoices/**",
                ADMIN_API + "/reservation/event/*/*/audit"
            };

            configurer.csrfTokenRepository(csrfTokenRepository)
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.GET, ADMIN_API + "/users/current").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(HttpMethod.POST, ADMIN_API + "/users/check", ADMIN_API + "/users/current/edit", ADMIN_API + "/users/current/update-password").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(ADMIN_API + "/configuration/**", ADMIN_API + "/users/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers(ADMIN_API + "/organizations/new").hasRole(ADMIN)
                .antMatchers(ADMIN_API + "/check-in/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(HttpMethod.GET, ownershipRequired).hasAnyRole(ADMIN, OWNER)
                .antMatchers(HttpMethod.GET, ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(HttpMethod.POST, ADMIN_API + "/reservation/event/*/new", ADMIN_API + "/reservation/event/*/*").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(HttpMethod.PUT, ADMIN_API + "/reservation/event/*/*/notify", ADMIN_API + "/reservation/event/*/*/confirm").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers(ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers("/admin/**/export/**").hasAnyRole(ADMIN, OWNER)
                .antMatchers("/admin/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
                .antMatchers("/api/attendees/**").denyAll()
                .antMatchers("/callback").permitAll()
                .antMatchers("/**").permitAll()
                .and()
                .formLogin()
                .loginPage("/authentication")
                .loginProcessingUrl("/authenticate")
                .failureUrl("/authentication?failed")
                .and().logout().permitAll();


            //
            http.addFilterBefore(new RecaptchaLoginFilter(recaptchaService, "/authenticate", "/authentication?recaptchaFailed", configurationManager), UsernamePasswordAuthenticationFilter.class);

            // call implementation-specific logic
            addAdditionalFilters(http);

            //FIXME create session and set csrf cookie if we are getting a v2 public api, an admin api call , will switch to pure cookie based
            http.addFilterBefore((servletRequest, servletResponse, filterChain) -> {

                HttpServletRequest req = (HttpServletRequest) servletRequest;
                HttpServletResponse res = (HttpServletResponse) servletResponse;
                var reqUri = req.getRequestURI();

                if ((reqUri.startsWith("/api/v2/public/") || reqUri.startsWith("/admin/api/") || reqUri.startsWith("/api/v2/admin/")) && "GET".equalsIgnoreCase(req.getMethod())) {
                    CsrfToken csrf = csrfTokenRepository.loadToken(req);
                    if (csrf == null) {
                        csrf = csrfTokenRepository.generateToken(req);
                    }
                    Cookie cookie = new Cookie("XSRF-TOKEN", csrf.getToken());
                    cookie.setPath("/");
                    res.addCookie(cookie);
                }
                filterChain.doFilter(servletRequest, servletResponse);
            }, RecaptchaLoginFilter.class);

            if (environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
                http.addFilterAfter(new UserCreatorBeforeLoginFilter(userManager, "/authenticate"), RecaptchaLoginFilter.class);
            }
        }

        /**
         * This method is called right after applying the {@link RecaptchaLoginFilter}
         * @param http
         */
        protected void addAdditionalFilters(HttpSecurity http) throws Exception {
        }

        protected UserManager getUserManager() {
            return userManager;
        }

        protected PasswordEncoder getPasswordEncoder() {
            return passwordEncoder;
        }
    }
}
