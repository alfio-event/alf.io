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

import alfio.config.support.OpenIdCallbackLoginFilter;
import alfio.manager.RecaptchaService;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.system.OpenIdAuthenticationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.ENABLE_CAPTCHA_FOR_LOGIN;

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

    private static boolean isTokenAuthentication(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.toLowerCase(Locale.ENGLISH).startsWith("apikey ");
    }

    @Bean
    public CsrfTokenRepository getCsrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setSessionAttributeName(CSRF_SESSION_ATTRIBUTE);
        repository.setParameterName(CSRF_PARAM_NAME);
        return repository;
    }

    private static class APIKeyAuthFilter extends AbstractPreAuthenticatedProcessingFilter {

        @Override
        protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
            return isTokenAuthentication(request) ? StringUtils.trim(request.getHeader("Authorization").substring("apikey ".length())) : null;
        }

        @Override
        protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
            return "N/A";
        }
    }

    public static class APITokenAuthentication extends AbstractAuthenticationToken {

        private final Object principal;
        private final Object credentials;


        public APITokenAuthentication(Object principal, Object credentials, Collection<? extends GrantedAuthority> authorities) {
            super(authorities);
            this.credentials = credentials;
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return credentials;
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }
    }

    public static class WrongAccountTypeException extends AccountStatusException {

        public WrongAccountTypeException(String msg) {
            super(msg);
        }
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
                if (!user.isCurrentlyValid(ZonedDateTime.now(ZoneId.of("UTC")))) {
                    throw new DisabledException("Api key " + apiKey + " is expired");
                }

                return new APITokenAuthentication(
                    authentication.getPrincipal(),
                    authentication.getCredentials(),
                    authorityRepository.findRoles(apiKey).stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
            });


            http.requestMatcher(WebSecurityConfig::isTokenAuthentication)
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
            super(environment, userManager, recaptchaService, configurationManager, csrfTokenRepository,
                dataSource, passwordEncoder, openIdAuthenticationManager, userRepository, authorityRepository,
                userOrganizationRepository, organizationRepository);
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
                                    PasswordEncoder passwordEncoder,
                                    UserRepository userRepository,
                                    AuthorityRepository authorityRepository,
                                    UserOrganizationRepository userOrganizationRepository,
                                    OrganizationRepository organizationRepository) {
            super(environment, userManager, recaptchaService, configurationManager,
                csrfTokenRepository, dataSource, passwordEncoder, null,
                userRepository, authorityRepository, userOrganizationRepository, organizationRepository);
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
        private final OpenIdAuthenticationManager openIdAuthenticationManager;
        private final UserRepository userRepository;
        private final AuthorityRepository authorityRepository;
        private final UserOrganizationRepository userOrganizationRepository;
        private final OrganizationRepository organizationRepository;

        @Override
        public void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.jdbcAuthentication().dataSource(dataSource)
                .usersByUsernameQuery("select username, password, enabled from ba_user where username = ?")
                .authoritiesByUsernameQuery("select username, role from authority where username = ?")
                .passwordEncoder(passwordEncoder);
            if (openIdAuthenticationManager != null) {
                auth.authenticationProvider(new OpenIdAuthenticationProvider());
            }
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
            if (openIdAuthenticationManager != null) {
                http.addFilterBefore(new OpenIdCallbackLoginFilter(openIdAuthenticationManager, new AntPathRequestMatcher("/callback", "GET"),
                    authenticationManager(), userRepository, authorityRepository, passwordEncoder, userManager, userOrganizationRepository,
                    organizationRepository), UsernamePasswordAuthenticationFilter.class);
                log.trace("adding openid filter");
                http.addFilterBefore(new OpenIdAuthenticationFilter("/authentication", openIdAuthenticationManager), RecaptchaLoginFilter.class);
            }


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

        private static class OpenIdAuthenticationProvider implements AuthenticationProvider {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                return authentication;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return authentication.equals(OpenIdAlfioAuthentication.class);
            }
        }

        private static class OpenIdAuthenticationFilter extends GenericFilterBean {
            private final RequestMatcher requestMatcher;
            private final OpenIdAuthenticationManager openIdAuthenticationManager;

            private OpenIdAuthenticationFilter(String loginURL, OpenIdAuthenticationManager openIdAuthenticationManager) {
                this.requestMatcher = new AntPathRequestMatcher(loginURL, "GET");
                this.openIdAuthenticationManager = openIdAuthenticationManager;
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse res = (HttpServletResponse) response;

                if (requestMatcher.matches(req)) {
                    if (SecurityContextHolder.getContext().getAuthentication() != null || req.getParameterMap().containsKey("logout")) {
                        res.sendRedirect("/admin/");
                        return;
                    }
                    log.trace("calling buildAuthorizeUrl");
                    res.sendRedirect(openIdAuthenticationManager.buildAuthorizeUrl());
                    return;
                }

                chain.doFilter(request, response);
            }
        }

        private static class RecaptchaLoginFilter extends GenericFilterBean {
            private final RequestMatcher requestMatcher;
            private final RecaptchaService recaptchaService;
            private final String recaptchaFailureUrl;
            private final ConfigurationManager configurationManager;


            RecaptchaLoginFilter(RecaptchaService recaptchaService,
                                 String loginProcessingUrl,
                                 String recaptchaFailureUrl,
                                 ConfigurationManager configurationManager) {
                this.requestMatcher = new AntPathRequestMatcher(loginProcessingUrl, "POST");
                this.recaptchaService = recaptchaService;
                this.recaptchaFailureUrl = recaptchaFailureUrl;
                this.configurationManager = configurationManager;
            }


            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) request;
                HttpServletResponse res = (HttpServletResponse) response;
                if (requestMatcher.matches(req) &&
                    configurationManager.getForSystem(ENABLE_CAPTCHA_FOR_LOGIN).getValueAsBooleanOrDefault() &&
                    !recaptchaService.checkRecaptcha(null, req)) {
                    res.sendRedirect(recaptchaFailureUrl);
                    return;
                }

                chain.doFilter(request, response);
            }
        }

        // generate a user if it does not exists, to be used by the demo profile
        private static class UserCreatorBeforeLoginFilter extends GenericFilterBean {

            private final UserManager userManager;
            private final RequestMatcher requestMatcher;

            UserCreatorBeforeLoginFilter(UserManager userManager, String loginProcessingUrl) {
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
                        int orgId = userManager.createOrganization(username, "Demo organization", username);
                        userManager.insertUser(orgId, username, "", "", username, Role.OWNER, User.Type.DEMO, req.getParameter("password"), null, null);
                    }
                }

                chain.doFilter(request, response);
            }
        }
    }

    public static class OpenIdAlfioAuthentication extends AbstractAuthenticationToken {
        private final String idToken;
        private final String subject;
        private final String email;
        private final String idpLogoutRedirectionUrl;

        public OpenIdAlfioAuthentication(Collection<? extends GrantedAuthority> authorities, String idToken, String subject, String email, String idpLogoutRedirectionUrl) {
            super(authorities);
            this.idToken = idToken;
            this.subject = subject;
            this.email = email;
            this.idpLogoutRedirectionUrl = idpLogoutRedirectionUrl;
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
    }

}
