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
package alfio.config.authentication;

import alfio.config.Initializer;
import alfio.config.authentication.support.*;
import alfio.manager.RecaptchaService;
import alfio.manager.openid.OpenIdAuthenticationManager;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpCookie;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

import javax.servlet.Filter;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static alfio.config.Initializer.API_V2_PUBLIC_PATH;
import static alfio.config.Initializer.XSRF_TOKEN;
import static alfio.config.authentication.support.AuthenticationConstants.*;
import static alfio.config.authentication.support.OpenIdAuthenticationFilter.*;

abstract class AbstractFormBasedWebSecurity {
    public static final String AUTHENTICATE = "/authenticate";
    private static final String[] OWNERSHIP_REQUIRED = new String[]{
        ADMIN_API + "/overridable-template",
        ADMIN_API + "/additional-services",
        ADMIN_API + "/events/*/additional-field",
        ADMIN_API + "/event/*/additional-services/",
        ADMIN_API + "/overridable-template/",
        ADMIN_API + "/events/*/promo-code",
        ADMIN_API + "/reservation/event/*/reservations/list",
        ADMIN_API + "/event/*/email/",
        ADMIN_API + "/event/*/waiting-queue/load",
        ADMIN_API + "/events/*/pending-payments",
        ADMIN_API + "/events/*/export",
        ADMIN_API + "/events/*/sponsor-scan/export",
        ADMIN_API + "/events/*/invoices/**",
        ADMIN_API + "/reservation/*/*/*/audit",
        ADMIN_API + "/subscription/*/email/",
        ADMIN_API + "/organization/*/subscription/**",
        ADMIN_API + "/reservation/subscription/**"
    };

    private final Environment environment;
    private final UserManager userManager;
    private final RecaptchaService recaptchaService;
    private final ConfigurationManager configurationManager;
    private final CsrfTokenRepository csrfTokenRepository;
    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final PublicOpenIdAuthenticationManager publicOpenIdAuthenticationManager;
    private final SpringSessionBackedSessionRegistry<?> sessionRegistry;

    protected AbstractFormBasedWebSecurity(Environment environment,
                                           UserManager userManager,
                                           RecaptchaService recaptchaService,
                                           ConfigurationManager configurationManager,
                                           CsrfTokenRepository csrfTokenRepository,
                                           DataSource dataSource,
                                           PasswordEncoder passwordEncoder,
                                           PublicOpenIdAuthenticationManager publicOpenIdAuthenticationManager,
                                           SpringSessionBackedSessionRegistry<?> sessionRegistry) {
        this.environment = environment;
        this.userManager = userManager;
        this.recaptchaService = recaptchaService;
        this.configurationManager = configurationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.publicOpenIdAuthenticationManager = publicOpenIdAuthenticationManager;
        this.sessionRegistry = sessionRegistry;
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        if (environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE))) {
            http.requiresChannel().antMatchers("/healthz").requiresInsecure()
                .and()
                .requiresChannel().mvcMatchers("/**").requiresSecure();
        }

        CsrfConfigurer<HttpSecurity> configurer = csrfConfigurer(http);

        var authenticationManager = createAuthenticationManager();
        configurer.csrfTokenRepository(csrfTokenRepository)
            .and()
            .headers().frameOptions().disable() // https://github.com/alfio-event/alf.io/issues/1031 X-Frame-Options has been moved to IndexController
            .and()
            .authorizeRequests(AbstractFormBasedWebSecurity::authorizeRequests)
            .authenticationManager(authenticationManager)
            .formLogin()
            .loginPage("/authentication")
            .loginProcessingUrl(AUTHENTICATE)
            .defaultSuccessUrl("/admin")
            .failureUrl("/authentication?failed")
            .and().logout().permitAll()
            .and()
            // this allows us to sync between spring session and spring security, thus saving the principal name in the session table
            .sessionManagement().maximumSessions(-1).sessionRegistry(sessionRegistry);

        http.addFilterBefore(openIdPublicCallbackLoginFilter(publicOpenIdAuthenticationManager, authenticationManager), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(openIdPublicAuthenticationFilter(publicOpenIdAuthenticationManager), AnonymousAuthenticationFilter.class);


        //
        http.addFilterBefore(new RecaptchaLoginFilter(recaptchaService, AUTHENTICATE, "/authentication?recaptchaFailed", configurationManager), UsernamePasswordAuthenticationFilter.class);

        // call implementation-specific logic
        addAdditionalFilters(http, authenticationManager);

        http.addFilterBefore(csrfFilter(), RecaptchaLoginFilter.class);

        if (environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
            http.addFilterAfter(new UserCreatorBeforeLoginFilter(userManager, AUTHENTICATE), RecaptchaLoginFilter.class);
        }

        return http.build();
    }

    private JdbcUserDetailsManager createUserDetailsManager() {
        var userDetailsManager = new JdbcUserDetailsManager(dataSource);
        userDetailsManager.setUsersByUsernameQuery("select username, password, enabled from ba_user where username = ?");
        userDetailsManager.setAuthoritiesByUsernameQuery("select username, role from authority where username = ?");
        return userDetailsManager;
    }

    protected final AuthenticationManager createAuthenticationManager() {
        var userDetailsManager = createUserDetailsManager();
        var daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        daoAuthenticationProvider.setUserDetailsService(userDetailsManager);
        return new ProviderManager(List.of(daoAuthenticationProvider, new OpenIdAuthenticationProvider()));
    }

    private Filter csrfFilter() {
        return (servletRequest, servletResponse, filterChain) -> {

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            var reqUri = req.getRequestURI();

            if ((reqUri.startsWith(API_V2_PUBLIC_PATH) || reqUri.startsWith(ADMIN_API) || reqUri.startsWith("/api/v2/admin/") || reqUri.equals(AUTHENTICATION_STATUS)) && "GET".equalsIgnoreCase(req.getMethod())) {
                CsrfToken csrf = csrfTokenRepository.loadToken(req);
                if (csrf == null) {
                    csrf = csrfTokenRepository.generateToken(req);
                }
                res.addHeader(XSRF_TOKEN, csrf.getToken());
                if (!reqUri.startsWith(API_V2_PUBLIC_PATH)) {
                    // FIXME remove this after the new admin is complete
                    addCookie(res, csrf);
                }
            }
            filterChain.doFilter(servletRequest, servletResponse);
        };
    }

    private static void authorizeRequests(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry auth) {
        auth.antMatchers(ADMIN_PUBLIC_API + "/**").denyAll() // Admin public API requests must be authenticated using API-Keys
            .antMatchers(HttpMethod.GET, ADMIN_API + "/users/current").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(HttpMethod.POST, ADMIN_API + "/users/check", ADMIN_API + "/users/current/edit", ADMIN_API + "/users/current/update-password").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(ADMIN_API + "/configuration/**", ADMIN_API + "/users/**").hasAnyRole(ADMIN, OWNER)
            .antMatchers(ADMIN_API + "/organizations/new", ADMIN_API + "/system/**").hasRole(ADMIN)
            .antMatchers(ADMIN_API + "/check-in/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(HttpMethod.GET, OWNERSHIP_REQUIRED).hasAnyRole(ADMIN, OWNER)
            .antMatchers(HttpMethod.GET, ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(HttpMethod.POST, ADMIN_API + "/reservation/event/*/new", ADMIN_API + "/reservation/event/*/*").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(HttpMethod.PUT,
                ADMIN_API + "/reservation/event/*/*/notify",
                ADMIN_API + "/reservation/event/*/*/notify-attendees",
                ADMIN_API + "/reservation/event/*/*/confirm"
            ).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers(ADMIN_API + "/**").hasAnyRole(ADMIN, OWNER)
            .antMatchers("/admin/**/export/**").hasAnyRole(ADMIN, OWNER)
            .antMatchers("/admin/**").hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .antMatchers("/api/attendees/**").denyAll()
            .antMatchers("/callback").permitAll()
            .antMatchers("/**").permitAll();
    }

    private static CsrfConfigurer<HttpSecurity> csrfConfigurer(HttpSecurity http) throws Exception {
        var configurer = http.exceptionHandling()
            .accessDeniedHandler((request, response, accessDeniedException) -> {
                if (!response.isCommitted()) {
                    if ("XMLHttpRequest".equals(request.getHeader(AuthenticationConstants.X_REQUESTED_WITH))) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    } else if (!response.isCommitted()) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        RequestDispatcher dispatcher = request.getRequestDispatcher("/session-expired");
                        dispatcher.forward(request, response);
                    }
                }
            })
            .defaultAuthenticationEntryPointFor((request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED), new RequestHeaderRequestMatcher(AuthenticationConstants.X_REQUESTED_WITH, "XMLHttpRequest"))
            .and()
            .headers().cacheControl().disable()
            .and()
            .csrf();
        Pattern methodsPattern = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");
        Predicate<HttpServletRequest> csrfAllowListPredicate = r -> r.getRequestURI().startsWith("/api/webhook/")
            || r.getRequestURI().startsWith("/api/payment/webhook/")
            || methodsPattern.matcher(r.getMethod()).matches();
        csrfAllowListPredicate = csrfAllowListPredicate.or(r -> r.getRequestURI().equals("/report-csp-violation"));
        configurer.requireCsrfProtectionMatcher(new NegatedRequestMatcher(csrfAllowListPredicate::test));
        return configurer;
    }

    private void addCookie(HttpServletResponse res, CsrfToken csrf) {
        Cookie cookie = new Cookie(XSRF_TOKEN, csrf.getToken());
        cookie.setPath("/");
        boolean prod = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));
        cookie.setSecure(prod);
        if (prod) {
            cookie.setComment(HttpCookie.SAME_SITE_STRICT_COMMENT);
        }
        res.addCookie(cookie);
    }

    /**
     * This method is called right after applying the {@link RecaptchaLoginFilter}
     *
     * @param http
     * @param jdbcAuthenticationManager
     */
    protected void addAdditionalFilters(HttpSecurity http, AuthenticationManager jdbcAuthenticationManager) {
    }

    private OpenIdAuthenticationFilter openIdPublicAuthenticationFilter(OpenIdAuthenticationManager openIdAuthenticationManager) {
        return new OpenIdAuthenticationFilter("/openid/authentication", openIdAuthenticationManager, "/", true);
    }

    private OpenIdCallbackLoginFilter openIdPublicCallbackLoginFilter(OpenIdAuthenticationManager openIdAuthenticationManager,
                                                                      AuthenticationManager jdbcAuthenticationManager) {
        var filter = new OpenIdCallbackLoginFilter(openIdAuthenticationManager,
            new AntPathRequestMatcher("/openid/callback", "GET"),
            jdbcAuthenticationManager);
        filter.setAuthenticationSuccessHandler((request, response, authentication) -> {
            var session = request.getSession();
            var reservationId = (String) session.getAttribute(RESERVATION_KEY);
            if(StringUtils.isNotBlank(reservationId)) {
                var contextTypeKey = session.getAttribute(CONTEXT_TYPE_KEY);
                var contextIdKey = session.getAttribute(CONTEXT_ID_KEY);
                session.removeAttribute(CONTEXT_TYPE_KEY);
                session.removeAttribute(CONTEXT_ID_KEY);
                session.removeAttribute(RESERVATION_KEY);
                response.sendRedirect("/"+contextTypeKey+"/"+ contextIdKey +"/reservation/"+reservationId+"/book");
            } else {
                response.sendRedirect("/");
            }
        });
        return filter;
    }
}
