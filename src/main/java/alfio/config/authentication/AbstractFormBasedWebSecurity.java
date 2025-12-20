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
import alfio.config.challenge.CFTurnstileVerificationFilter;
import alfio.config.support.ContextAwareCookieSerializer;
import alfio.manager.RecaptchaService;
import alfio.manager.openid.OpenIdConfiguration;
import alfio.manager.payment.MollieConnectManager;
import alfio.manager.payment.StripeConnectManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.user.User;
import alfio.util.Json;
import alfio.util.TemplateManager;
import jakarta.servlet.Filter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.*;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static alfio.config.Initializer.API_V2_PUBLIC_PATH;
import static alfio.config.Initializer.XSRF_TOKEN;
import static alfio.config.WebSecurityConfig.CSRF_PARAM_NAME;
import static alfio.config.authentication.support.AuthenticationConstants.*;
import static alfio.config.authentication.support.PublicOpenIdRequestResolver.OPENID_AUTHENTICATION_PATH;
import static alfio.config.authentication.support.UserProvidedClientRegistrationRepository.OPENID_CALLBACK_PATH;
import static alfio.model.system.ConfigurationKeys.OPENID_CONFIGURATION_JSON;

abstract class AbstractFormBasedWebSecurity {
    public static final String AUTHENTICATE = "/authenticate";
    private static final Logger log = LoggerFactory.getLogger(AbstractFormBasedWebSecurity.class);
    private static final List<String> OWNERSHIP_REQUIRED = List.of(
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
    );

    private final Environment environment;
    protected final UserManager userManager;
    private final RecaptchaService recaptchaService;
    private final ConfigurationManager configurationManager;
    private final CsrfTokenRepository csrfTokenRepository;
    private final DataSource dataSource;
    protected final PasswordEncoder passwordEncoder;
    private final SpringSessionBackedSessionRegistry<?> sessionRegistry;
    protected final OpenIdUserSynchronizer openIdUserSynchronizer;
    protected final ContextAwareCookieSerializer cookieSerializer;
    protected final TemplateManager templateManager;

    protected AbstractFormBasedWebSecurity(Environment environment,
                                           UserManager userManager,
                                           RecaptchaService recaptchaService,
                                           ConfigurationManager configurationManager,
                                           CsrfTokenRepository csrfTokenRepository,
                                           DataSource dataSource,
                                           PasswordEncoder passwordEncoder,
                                           SpringSessionBackedSessionRegistry<?> sessionRegistry,
                                           OpenIdUserSynchronizer openIdUserSynchronizer,
                                           CookieSerializer cookieSerializer,
                                           TemplateManager templateManager) {
        this.environment = environment;
        this.userManager = userManager;
        this.recaptchaService = recaptchaService;
        this.configurationManager = configurationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.dataSource = dataSource;
        this.passwordEncoder = passwordEncoder;
        this.sessionRegistry = sessionRegistry;
        this.openIdUserSynchronizer = openIdUserSynchronizer;
        this.cookieSerializer = (ContextAwareCookieSerializer) cookieSerializer;
        this.templateManager = templateManager;
    }

    @Bean
    public SecurityFilterChain publicOpenId(HttpSecurity http) throws Exception {
        configureExceptionHandling(http);
        configureCsrf(http, configurer -> configurer.csrfTokenRepository(csrfTokenRepository));
        http.securityMatcher(antStyleMatcher("/openid/**"))
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            // this allows us to sync between spring session and spring security, thus saving the principal name in the session table
            .sessionManagement(management -> management.maximumSessions(-1).sessionRegistry(sessionRegistry));


        var registrationRepository = new UserProvidedClientRegistrationRepository(configurationManager);
        http.oauth2Login(oauth -> oauth
            .loginPage(OPENID_AUTHENTICATION_PATH)
            .loginProcessingUrl(OPENID_CALLBACK_PATH)
            .clientRegistrationRepository(registrationRepository)
            .authorizationEndpoint(auth -> auth.authorizationRequestResolver(
                new PublicOpenIdRequestResolver(registrationRepository)
            ))
            .userInfoEndpoint(uie -> uie.oidcUserService(publicOidcUserService(configurationManager)))
            .successHandler(new OpenIdLoginSuccessHandler(templateManager, cookieSerializer))
        );
        http.addFilterBefore(new PreAuthCookieWriterFilter(cookieSerializer, antStyleMatcher(OPENID_AUTHENTICATION_PATH)), OAuth2AuthorizationRequestRedirectFilter.class);

        disableRequestCacheParameter(http);

        return http.build();
    }


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, StripeConnectManager stripeConnectManager, MollieConnectManager mollieConnectManager) throws Exception {
        if (environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE))) {
            http.redirectToHttps(customizer -> customizer.requestMatchers(new NegatedRequestMatcher(antStyleMatcher("/healthz"))));
        }

        configureExceptionHandling(http);
        configureCsrf(http, configurer -> configurer.csrfTokenRepository(csrfTokenRepository));
        http.securityMatcher("/**").headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
            .authorizeHttpRequests(AbstractFormBasedWebSecurity::authorizeRequests)
            // this allows us to sync between spring session and spring security, thus saving the principal name in the session table
            .sessionManagement(management -> management.maximumSessions(-1).sessionRegistry(sessionRegistry));

        setupAuthenticationEndpoint(http);
        //
        http.addFilterBefore(new RecaptchaLoginFilter(recaptchaService, AUTHENTICATE, "/authentication?recaptchaFailed", configurationManager), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new CFTurnstileVerificationFilter(configurationManager, new OrRequestMatcher(
                // event
                antStyleMatcher(HttpMethod.POST, "/api/v2/public/event/{name}/reserve-tickets"),
                // subscription
                antStyleMatcher(HttpMethod.POST, "/api/v2/public/subscription/{id}")
            )), RecaptchaLoginFilter.class)
            .addFilterAfter(new PaymentProviderConnectFilter(templateManager, userManager, stripeConnectManager, mollieConnectManager), CFTurnstileVerificationFilter.class);

        if (environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO))) {
            http.addFilterAfter(new UserCreatorBeforeLoginFilter(userManager, AUTHENTICATE), RecaptchaLoginFilter.class);
        }

        disableRequestCacheParameter(http);

        return http.addFilterAfter(csrfPublisherFilter(), CsrfFilter.class).build();
    }

    // see https://stackoverflow.com/questions/75222930/spring-boot-3-0-2-adds-continue-query-parameter-to-request-url-after-login
    private static void disableRequestCacheParameter(HttpSecurity http) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        http.requestCache(cache -> cache.requestCache(requestCache));
    }

    private OAuth2UserService<OidcUserRequest, OidcUser> publicOidcUserService(ConfigurationManager configurationManager) {
        final OidcUserService delegate = new OidcUserService();
        return userRequest -> {
            OidcUser oidcUser = delegate.loadUser(userRequest);
            var alfioUser = new OpenIdAlfioUser(null, oidcUser.getSubject(), oidcUser.getEmail(), User.Type.PUBLIC, Set.of(), Map.of());
            var configuration = Json.fromJson(configurationManager.getPublicOpenIdConfiguration().get(OPENID_CONFIGURATION_JSON).getRequiredValue(), OpenIdConfiguration.class);
            boolean signedUp = openIdUserSynchronizer.syncUser(oidcUser, alfioUser, configuration);
            return new OpenIdPrincipal(List.of(), oidcUser.getIdToken(), oidcUser.getUserInfo(), alfioUser, buildLogoutUrl(configuration), signedUp);
        };
    }

    protected void setupAuthenticationEndpoint(HttpSecurity http) throws Exception {
        http.authenticationManager(createAuthenticationManager())
            .formLogin(login -> login
                .loginPage("/authentication")
                .loginProcessingUrl(AUTHENTICATE)
                .defaultSuccessUrl("/admin")
                .failureUrl("/authentication?failed")).logout(LogoutConfigurer::permitAll);
    }

    private JdbcUserDetailsManager createUserDetailsManager() {
        var userDetailsManager = new JdbcUserDetailsManager(dataSource);
        userDetailsManager.setUsersByUsernameQuery("select username, password, enabled from ba_user where username = ?");
        userDetailsManager.setAuthoritiesByUsernameQuery("select username, role from authority where username = ?");
        return userDetailsManager;
    }

    protected final AuthenticationManager createAuthenticationManager() {
        var userDetailsManager = createUserDetailsManager();
        var daoAuthenticationProvider = new DaoAuthenticationProvider(userDetailsManager);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(List.of(daoAuthenticationProvider));
    }

    private Filter csrfPublisherFilter() {
        return (servletRequest, servletResponse, filterChain) -> {

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            var reqUri = req.getRequestURI();
            if ((reqUri.startsWith(API_V2_PUBLIC_PATH) || reqUri.startsWith(ADMIN_API) || reqUri.startsWith("/api/v2/admin/") || reqUri.equals(AUTHENTICATION_STATUS)) && "GET".equalsIgnoreCase(req.getMethod())) {
                CsrfToken token = (CsrfToken) req.getAttribute(CSRF_PARAM_NAME);
                if (token != null) {
                    String csrfToken = token.getToken();
                    res.addHeader(XSRF_TOKEN, csrfToken);
                    if (!reqUri.startsWith(API_V2_PUBLIC_PATH)) {
                        // FIXME remove this after the new admin is complete
                        addCookie(res, csrfToken);
                    }
                } else {
                    log.warn("Expected CSRF token for request {} but none found.", reqUri);
                }
            }
            filterChain.doFilter(servletRequest, servletResponse);
        };
    }

    private static void authorizeRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(antStyleMatcher(ADMIN_PUBLIC_API + "/**")).denyAll() // Admin public API requests must be authenticated using API-Keys
            .requestMatchers(antStyleMatcher(HttpMethod.GET, ADMIN_API + "/users/current")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher(HttpMethod.POST, ADMIN_API + "/users/check"), antStyleMatcher(HttpMethod.POST, ADMIN_API + "/users/current/edit"), antStyleMatcher(HttpMethod.POST, ADMIN_API + "/users/current/update-password")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher(ADMIN_API + "/configuration/**"), antStyleMatcher(ADMIN_API + "/users/**")).hasAnyRole(ADMIN, OWNER)
            .requestMatchers(antStyleMatcher(ADMIN_API + "/organizations/new"), antStyleMatcher(ADMIN_API + "/system/**")).hasRole(ADMIN)
            .requestMatchers(antStyleMatcher(ADMIN_API + "/check-in/**")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(OWNERSHIP_REQUIRED.stream().map(u -> antStyleMatcher(HttpMethod.GET, u)).toArray(RequestMatcher[]::new)).hasAnyRole(ADMIN, OWNER)
            .requestMatchers(antStyleMatcher(HttpMethod.GET, ADMIN_API + "/**")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher(HttpMethod.POST, ADMIN_API + "/reservation/event/*/new"), antStyleMatcher(HttpMethod.POST,ADMIN_API + "/reservation/event/*/*")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher(HttpMethod.PUT, ADMIN_API + "/reservation/event/*/*/notify"), antStyleMatcher(HttpMethod.PUT, ADMIN_API + "/reservation/event/*/*/notify-attendees"), antStyleMatcher(HttpMethod.PUT,ADMIN_API + "/reservation/event/*/*/confirm")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher(ADMIN_API + "/**")).hasAnyRole(ADMIN, OWNER)
            .requestMatchers(antStyleMatcher("/admin/**")).hasAnyRole(ADMIN, OWNER, SUPERVISOR)
            .requestMatchers(antStyleMatcher("/api/attendees/**")).denyAll()
            .requestMatchers(antStyleMatcher("/callback")).permitAll()
            .requestMatchers(antStyleMatcher("/**")).permitAll();
    }

    private static RequestMatcher antStyleMatcher(String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(pattern);
    }

    private static RequestMatcher antStyleMatcher(HttpMethod method, String pattern) {
        return PathPatternRequestMatcher.withDefaults().matcher(method, pattern);
    }

    private static void configureExceptionHandling(HttpSecurity http) throws Exception {
        http.exceptionHandling(handling -> handling
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if(!response.isCommitted()) {
                        if("XMLHttpRequest".equals(request.getHeader(AuthenticationConstants.X_REQUESTED_WITH))) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        } else if(!response.isCommitted()) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            RequestDispatcher dispatcher = request.getRequestDispatcher("/session-expired");
                            dispatcher.forward(request, response);
                        }
                    }
                })
                .defaultAuthenticationEntryPointFor((request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED), new RequestHeaderRequestMatcher(AuthenticationConstants.X_REQUESTED_WITH, "XMLHttpRequest")))
            .headers(headers -> headers.cacheControl(HeadersConfigurer.CacheControlConfig::disable));
    }

    private static void configureCsrf(HttpSecurity http, Consumer<CsrfConfigurer<HttpSecurity>> additionalConfiguration) throws Exception {
         http.csrf(c -> {
            Pattern methodsPattern = Pattern.compile("^(GET|HEAD|TRACE|OPTIONS)$");
            Predicate<HttpServletRequest> csrfAllowListPredicate = r -> r.getRequestURI().startsWith("/api/webhook/")
                || r.getRequestURI().startsWith("/api/payment/webhook/")
                || methodsPattern.matcher(r.getMethod()).matches();
            csrfAllowListPredicate = csrfAllowListPredicate.or(r -> r.getRequestURI().equals("/report-csp-violation"));
            c.requireCsrfProtectionMatcher(new NegatedRequestMatcher(csrfAllowListPredicate::test));
            additionalConfiguration.accept(c);
        });
    }

    private void addCookie(HttpServletResponse res, String csrfToken) {
        Cookie cookie = new Cookie(XSRF_TOKEN, csrfToken);
        cookie.setPath("/");
        boolean prod = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));
        cookie.setSecure(prod);
        if (prod) {
            cookie.setAttribute("SameSite", "Strict");
        }
        res.addCookie(cookie);
    }

    protected Environment environment() {
        return environment;
    }

    protected ConfigurationManager configurationManager() {
        return configurationManager;
    }

    protected static String buildLogoutUrl(OpenIdConfiguration openIdConfiguration) {
        UriComponents uri = UriComponentsBuilder.newInstance()
            .scheme("https")
            .host(openIdConfiguration.domain())
            .path(openIdConfiguration.logoutUrl())
            .queryParam("redirect_uri", openIdConfiguration.logoutRedirectUrl())
            .build();
        return uri.toString();
    }
}
