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

import alfio.config.authentication.support.APIKeyAuthFilter;
import alfio.config.authentication.support.APITokenAuthentication;
import alfio.config.authentication.support.RequestTypeMatchers;
import alfio.config.authentication.support.WrongAccountTypeException;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.util.ClockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.List;

import static alfio.config.authentication.support.AuthenticationConstants.*;

@Configuration(proxyBeanMethods = false)
public class APITokenAuthWebSecurity {

    public static final String API_KEY = "Api key ";
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final ConfigurationManager configurationManager;

    public APITokenAuthWebSecurity(UserRepository userRepository,
                                   AuthorityRepository authorityRepository,
                                   ConfigurationManager configurationManager) {
        this.userRepository = userRepository;
        this.authorityRepository = authorityRepository;
        this.configurationManager = configurationManager;
    }

    //https://stackoverflow.com/a/48448901
    @Bean
    @Order(Integer.MIN_VALUE) // APIKey Authentication needs to have the highest priority
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {

        APIKeyAuthFilter filter = new APIKeyAuthFilter();
        filter.setAuthenticationManager(authentication -> {
            //
            String apiKey = (String) authentication.getPrincipal();

            // check if API Key is system
            var systemApiKeyOptional = configurationManager.getForSystem(ConfigurationKeys.SYSTEM_API_KEY).getValue();

            if (systemApiKeyOptional.isPresent() && apiKeyMatches(apiKey, systemApiKeyOptional.get())) {
                return new APITokenAuthentication(
                    authentication.getPrincipal(),
                    authentication.getCredentials(),
                    List.of(new SimpleGrantedAuthority("ROLE_" + SYSTEM_API_CLIENT)));
            }

            //check if user type ->
            User user = userRepository.findByUsername(apiKey).orElseThrow(() -> new BadCredentialsException(API_KEY + apiKey + " don't exists"));
            if (!user.isEnabled()) {
                throw new DisabledException(API_KEY + apiKey + " is disabled");
            }
            if (User.Type.API_KEY != user.getType()) {
                throw new WrongAccountTypeException("Wrong account type for username " + apiKey);
            }
            if (!user.isCurrentlyValid(ZonedDateTime.now(ClockProvider.clock()))) {
                throw new DisabledException(API_KEY + apiKey + " is expired");
            }

            return new APITokenAuthentication(
                authentication.getPrincipal(),
                authentication.getCredentials(),
                authorityRepository.findRoles(apiKey).stream().map(SimpleGrantedAuthority::new).toList());
        });

        // do NOT attempt to persist context in session
        filter.setSecurityContextRepository(new NullSecurityContextRepository());

        return http.securityMatchers(matchers -> matchers.requestMatchers(RequestTypeMatchers::isTokenAuthentication))
            .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(APITokenAuthWebSecurity::configureMatchers)
            .addFilter(filter)
            .build();
    }

    private static void configureMatchers(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers(ADMIN_PUBLIC_API + "/system/**").hasRole(SYSTEM_API_CLIENT)
            .requestMatchers(ADMIN_PUBLIC_API + "/**").hasRole(API_CLIENT)
            .requestMatchers(ADMIN_API + "/check-in/event/*/attendees").hasRole(SUPERVISOR)
            .requestMatchers(ADMIN_API + "/check-in/*/label-layout").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
            .requestMatchers(ADMIN_API + "/check-in/**").hasAnyRole(OPERATOR, SUPERVISOR)
            .requestMatchers(HttpMethod.GET, ADMIN_API + "/events").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
            .requestMatchers(HttpMethod.GET, ADMIN_API + "/user-type", ADMIN_API + "/user/details").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
            .requestMatchers(ADMIN_API + "/**").denyAll()
            .requestMatchers(HttpMethod.POST, "/api/attendees/sponsor-scan").hasRole(SPONSOR)
            .requestMatchers(HttpMethod.GET, "/api/attendees/*/ticket/*").hasAnyRole(OPERATOR, SUPERVISOR, SPONSOR)
            .requestMatchers("/**").authenticated();
    }

    private static boolean apiKeyMatches(String input, String configurationValue) {
        return MessageDigest.isEqual(input.getBytes(StandardCharsets.UTF_8),
            configurationValue.getBytes(StandardCharsets.UTF_8));
    }
}
