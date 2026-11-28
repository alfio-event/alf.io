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

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.controller.api.admin.UsersApiController;
import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.model.user.UserWithPassword;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.test.config.AccessServiceMvcConfiguration;
import alfio.test.config.AccessServiceWebSecurityConfiguration;
import alfio.test.util.AlfioIntegrationTest;
import alfio.util.PasswordGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Logs in through the real security filter chain, backed by the JDBC session store, and checks that the sessions of a user
 * are closed when their password changes or their account is disabled.
 */
@AlfioIntegrationTest
@ContextConfiguration(classes = {
    DataSourceConfiguration.class,
    TestConfiguration.class,
    AccessServiceMvcConfiguration.class,
    WebSecurityConfig.class,
    AccessServiceWebSecurityConfiguration.class,
    SessionInvalidationIntegrationTest.WebServerConfiguration.class
})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class SessionInvalidationIntegrationTest {

    private static final String CURRENT_USER_URL = "/admin/api/users/current";

    /**
     * Serves the user API behind the session and security filters. It doesn't scan alfio.controller, which holds
     * a test configuration that disables security
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(UsersApiController.class)
    static class WebServerConfiguration implements WebMvcConfigurer {

        private final JsonMapper jsonMapper;

        WebServerConfiguration(JsonMapper jsonMapper) {
            this.jsonMapper = jsonMapper;
        }

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(new JacksonJsonHttpMessageConverter(jsonMapper));
        }

        @Bean
        SessionRepositoryFilter<?> springSessionRepositoryFilter(JdbcIndexedSessionRepository sessionRepository) {
            return new SessionRepositoryFilter<>(sessionRepository);
        }

        @Bean
        DispatcherServlet dispatcherServlet() {
            return new DispatcherServlet();
        }

        @Bean
        ServletRegistrationBean<DispatcherServlet> dispatcherServletRegistration(DispatcherServlet dispatcherServlet) {
            return new ServletRegistrationBean<>(dispatcherServlet, "/");
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    UserManager userManager;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuthorityRepository authorityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    private UserWithPassword user;
    private Principal admin;

    @BeforeEach
    void setUp() {
        int organizationId = userManager.createOrganization(new OrganizationModification(null, "org", "email@example.com", "desc", null, null), null);
        user = userManager.insertUser(organizationId, "session-user", "Session", "User", "user@example.com", Role.OWNER, User.Type.INTERNAL, null);
        userRepository.create(UserManager.ADMIN_USERNAME, passwordEncoder.encode("admin-password"), "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
        admin = new UsernamePasswordAuthenticationToken(UserManager.ADMIN_USERNAME, null, List.of(new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.getRoleName())));
    }

    @AfterEach
    void removeSessions() {
        jdbcTemplate.update("delete from alfio_spring_session", Map.of());
    }

    @Test
    void loginStoresThePrincipalNameOfTheSession() throws Exception {
        var browser = new Browser();
        browser.login(user.getUsername(), user.getPassword());

        var principalName = jdbcTemplate.queryForObject("select principal_name from alfio_spring_session where session_id = :id",
            Map.of("id", browser.sessionId()), String.class);
        assertEquals(user.getUsername(), principalName);
    }

    @Test
    void passwordResetByAnAdministratorClosesTheSessionsOfTheUser() throws Exception {
        var browser = new Browser();
        browser.login(user.getUsername(), user.getPassword());
        assertEquals(200, browser.get(CURRENT_USER_URL));

        userManager.resetPassword(user.getId(), admin);

        assertEquals(401, browser.get(CURRENT_USER_URL));
    }

    @Test
    void disablingTheUserClosesTheirSessions() throws Exception {
        var browser = new Browser();
        browser.login(user.getUsername(), user.getPassword());
        assertEquals(200, browser.get(CURRENT_USER_URL));

        userManager.enable(user.getId(), false, admin);

        assertEquals(401, browser.get(CURRENT_USER_URL));
    }

    @Test
    void changingTheOwnPasswordClosesTheOtherSessionsOnly() throws Exception {
        var laptop = new Browser();
        laptop.login(user.getUsername(), user.getPassword());
        var phone = new Browser();
        phone.login(user.getUsername(), user.getPassword());
        assertEquals(200, phone.get(CURRENT_USER_URL));

        var newPassword = PasswordGenerator.generateRandomPassword();
        var payload = "{\"oldPassword\":\"" + user.getPassword() + "\",\"newPassword\":\"" + newPassword + "\",\"newPasswordConfirm\":\"" + newPassword + "\"}";
        var response = laptop.postJson(CURRENT_USER_URL + "/update-password", payload);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"), response.body());

        assertEquals(200, laptop.get(CURRENT_USER_URL), "the session that changed the password must stay open");
        assertEquals(401, phone.get(CURRENT_USER_URL), "the other sessions must be closed");
    }

    /**
     * A client with its own cookies, as a browser on a different device
     */
    private class Browser {

        private final CookieManager cookies = new CookieManager();
        private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(cookies)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        void login(String username, String password) throws Exception {
            var form = "username=" + encode(username) + "&password=" + encode(password) + "&_csrf=" + encode(csrfToken());
            var request = HttpRequest.newBuilder(uri("/authenticate"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.discarding());
            assertEquals(302, response.statusCode());
            assertEquals("/admin", URI.create(response.headers().firstValue("Location").orElseThrow()).getPath(), "login failed");
        }

        int get(String path) throws Exception {
            var request = HttpRequest.newBuilder(uri(path))
                .header("X-Requested-With", "XMLHttpRequest")
                .GET()
                .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }

        HttpResponse<String> postJson(String path, String json) throws Exception {
            var request = HttpRequest.newBuilder(uri(path))
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        /**
         * The id of the session, as stored in the database: the cookie holds it Base64-encoded
         */
        String sessionId() {
            var encoded = cookie("SESSION");
            return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        }

        /**
         * A CSRF token accepted by the server. A POST without a token is refused, but it makes the server issue
         * the XSRF-TOKEN cookie, whose value then goes in the request in its masked form
         */
        private String csrfToken() throws Exception {
            var request = HttpRequest.newBuilder(uri("/authenticate"))
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            client.send(request, HttpResponse.BodyHandlers.discarding());
            var token = cookie("XSRF-TOKEN").getBytes(StandardCharsets.UTF_8);
            // masked form: random bytes, followed by the token XOR-ed with them. Zero bytes leave the token unchanged
            var masked = new byte[token.length * 2];
            System.arraycopy(token, 0, masked, token.length, token.length);
            return Base64.getUrlEncoder().encodeToString(masked);
        }

        private String cookie(String name) {
            return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("cookie " + name + " not found"));
        }

        private URI uri(String path) {
            return URI.create("http://localhost:" + port + path);
        }

        private String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
