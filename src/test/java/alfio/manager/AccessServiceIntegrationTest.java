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
package alfio.manager;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.config.authentication.FormBasedWebSecurity;
import alfio.config.authentication.support.APITokenAuthentication;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.support.AccessDeniedException;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.test.util.AlfioIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.List;

import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, AccessServiceIntegrationTest.AdHocMvcConfiguration.class, WebSecurityConfig.class, AccessServiceIntegrationTest.CustomContextConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class AccessServiceIntegrationTest {

    static class AdHocMvcConfiguration {
        @Bean
        public SpringSessionBackedSessionRegistry<?> sessionRegistry(FindByIndexNameSessionRepository<?> sessionRepository) {
            return new SpringSessionBackedSessionRegistry<>(sessionRepository);
        }
    }


    static class CustomContextConfiguration extends FormBasedWebSecurity {
        public CustomContextConfiguration(Environment environment,
                                          UserManager userManager,
                                          RecaptchaService recaptchaService,
                                          ConfigurationManager configurationManager,
                                          CsrfTokenRepository csrfTokenRepository,
                                          DataSource dataSource,
                                          PasswordEncoder passwordEncoder,
                                          PublicOpenIdAuthenticationManager publicOpenIdAuthenticationManager,
                                          SpringSessionBackedSessionRegistry<?> sessionRegistry) {
            super(environment, userManager, recaptchaService, configurationManager, csrfTokenRepository, dataSource, passwordEncoder, publicOpenIdAuthenticationManager, sessionRegistry);
        }

        @Bean
        public AuthenticationManager authenticationManagerBean() {
            return super.createAuthenticationManager();
        }
    }

    @Autowired
    UserManager userManager;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    AccessService accessService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuthorityRepository authorityRepository;

    @Autowired
    PasswordEncoder passwordEncoder;


    private Principal getFromUsernameAndPassword(String username, String password) {
        var token = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(token);
    }

    @Test
    void checkAccessForOrgOwner() {
        var org1Id = userManager.createOrganization(new OrganizationModification(null, "org1", "email", "desc", null, null), null);
        var org2Id = userManager.createOrganization(new OrganizationModification(null, "org2", "email", "desc", null, null), null);
        var userOrg1 = userManager.insertUser(org1Id, "userForOrg1", "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL, null);
        var userOrg2 = userManager.insertUser(org2Id, "userForOrg2", "test", "test", "test@example.com", Role.OWNER, User.Type.INTERNAL, null);

        // admin
        userRepository.create(UserManager.ADMIN_USERNAME, passwordEncoder.encode("abcd"), "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
        authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
        //


        var principalAdmin = getFromUsernameAndPassword("admin", "abcd");
        var principalUserOrg1 = getFromUsernameAndPassword(userOrg1.getUsername(), userOrg1.getPassword());
        var principalUserOrg2 = getFromUsernameAndPassword(userOrg2.getUsername(), userOrg2.getPassword());

        // no principal can access, in case of jobs & co
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(null, org1Id));
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(null, org2Id));

        // System api key can access both orgs
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(new APITokenAuthentication("TEST", "", List.of(new SimpleGrantedAuthority("ROLE_" + SYSTEM_API_CLIENT))), org1Id));
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(new APITokenAuthentication("TEST", "", List.of(new SimpleGrantedAuthority("ROLE_" + SYSTEM_API_CLIENT))), org2Id));

        // Admin can access both orgs
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(principalAdmin, org1Id));
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(principalAdmin, org2Id));


        // user org 1 can access
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(principalUserOrg1, org1Id));
        // user org 2 cannot access
        Assertions.assertThrows(AccessDeniedException.class, () -> accessService.checkOrganizationOwnership(principalUserOrg2, org1Id));

        // vice versa
        Assertions.assertDoesNotThrow(() -> accessService.checkOrganizationOwnership(principalUserOrg2, org2Id));
        Assertions.assertThrows(AccessDeniedException.class, () -> accessService.checkOrganizationOwnership(principalUserOrg1, org2Id));

    }
}
