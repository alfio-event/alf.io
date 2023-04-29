package alfio.manager;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.config.WebSecurityConfig;
import alfio.config.authentication.FormBasedWebSecurity;
import alfio.manager.openid.PublicOpenIdAuthenticationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.user.Role;
import alfio.model.user.User;
import alfio.test.util.AlfioIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.security.Principal;

@AlfioIntegrationTest
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, WebSecurityConfig.class, AccessServiceIntegrationTest.CustomContextConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class AccessServiceIntegrationTest {

    @EnableWebMvc
    @Configuration
    static class CustomContextConfiguration extends FormBasedWebSecurity {
        public CustomContextConfiguration(Environment environment,
                                          UserManager userManager,
                                          RecaptchaService recaptchaService,
                                          ConfigurationManager configurationManager,
                                          CsrfTokenRepository csrfTokenRepository,
                                          DataSource dataSource,
                                          PasswordEncoder passwordEncoder,
                                          PublicOpenIdAuthenticationManager publicOpenIdAuthenticationManager) {
            super(environment, userManager, recaptchaService, configurationManager, csrfTokenRepository, dataSource, passwordEncoder, publicOpenIdAuthenticationManager);
        }

        @Override
        @Bean
        public AuthenticationManager authenticationManagerBean () throws Exception {
            return super.authenticationManagerBean();
        }

    }

    @Autowired
    UserManager userManager;

    @Autowired
    AuthenticationManager authenticationManager;


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


        var principalUserOrg1 = getFromUsernameAndPassword(userOrg1.getUsername(), userOrg1.getPassword());
        var principalUserOrg2 = getFromUsernameAndPassword(userOrg2.getUsername(), userOrg2.getPassword());

        // FIXME test

    }
}
