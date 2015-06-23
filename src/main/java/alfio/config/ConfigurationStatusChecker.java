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

import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.system.Configuration;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.UserRepository;
import alfio.util.PasswordGenerator;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static alfio.model.system.ConfigurationKeys.*;
import static java.util.Optional.ofNullable;

@Component
@Log4j2
public class ConfigurationStatusChecker implements ApplicationListener<ContextRefreshedEvent> {

    private final ConfigurationManager configurationManager;
    private final UserRepository userRepository;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final String version;

    @Autowired
    public ConfigurationStatusChecker(ConfigurationManager configurationManager,
                                      UserRepository userRepository,
                                      AuthorityRepository authorityRepository,
                                      PasswordEncoder passwordEncoder,
                                      @Value("${alfio.version}") String version) {
        this.configurationManager = configurationManager;
        this.authorityRepository = authorityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.version = version;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        boolean initCompleted = configurationManager.getBooleanConfigValue(Configuration.system(), INIT_COMPLETED.getValue(), false);
        if (!initCompleted) {
            String adminPassword = PasswordGenerator.generateRandomPassword();
            userRepository.create(UserManager.ADMIN_USERNAME, passwordEncoder.encode(adminPassword), "The", "Administrator", "admin@localhost", true);
            authorityRepository.create(UserManager.ADMIN_USERNAME, AuthorityRepository.ROLE_ADMIN);
            log.info("*******************************************************");
            log.info("   This is the first time you're running alf.io");
            log.info("   here the generated admin credentials:");
            log.info("   {} ", adminPassword);
            log.info("*******************************************************");

            configurationManager.save(INIT_COMPLETED, "true");
            
            ofNullable(System.getProperty("maps.serverApiKey")).ifPresent((serverApiKey) -> configurationManager.save(MAPS_SERVER_API_KEY, serverApiKey));
            ofNullable(System.getProperty("maps.clientApiKey")).ifPresent((clientApiKey) -> configurationManager.save(MAPS_CLIENT_API_KEY, clientApiKey));
        }
        log.info("initialized alf.io version {} ", version);
    }
}
