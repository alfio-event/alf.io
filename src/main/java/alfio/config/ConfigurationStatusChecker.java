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
import alfio.manager.system.DataMigrator;
import alfio.manager.user.UserManager;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Role;
import alfio.model.user.User;
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
    private final DataMigrator dataMigrator;

    @Autowired
    public ConfigurationStatusChecker(ConfigurationManager configurationManager,
                                      UserRepository userRepository,
                                      AuthorityRepository authorityRepository,
                                      PasswordEncoder passwordEncoder,
                                      @Value("${alfio.version}") String version,
                                      DataMigrator dataMigrator) {
        this.configurationManager = configurationManager;
        this.authorityRepository = authorityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.version = version;
        this.dataMigrator = dataMigrator;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        boolean initCompleted = configurationManager.getForSystem(ConfigurationKeys.INIT_COMPLETED).getValueAsBooleanOrDefault();
        if (!initCompleted) {
            String adminPassword = PasswordGenerator.generateRandomPassword();
            userRepository.create(UserManager.ADMIN_USERNAME, passwordEncoder.encode(adminPassword), "The", "Administrator", "admin@localhost", true, User.Type.INTERNAL, null, null);
            authorityRepository.create(UserManager.ADMIN_USERNAME, Role.ADMIN.getRoleName());
            log.info("*******************************************************");
            log.info("   This is the first time you're running alf.io");
            log.info("   here the generated admin credentials:");
            log.info("   {} ", adminPassword);
            log.info("*******************************************************");

            configurationManager.saveSystemConfiguration(INIT_COMPLETED, "true");

            ofNullable(System.getProperty("maps.clientApiKey")).ifPresent(clientApiKey -> configurationManager.saveSystemConfiguration(MAPS_CLIENT_API_KEY, clientApiKey));
            ofNullable(System.getProperty("recaptcha.apiKey")).ifPresent(clientApiKey -> configurationManager.saveSystemConfiguration(RECAPTCHA_API_KEY, clientApiKey));
            ofNullable(System.getProperty("recaptcha.secret")).ifPresent(clientApiKey -> configurationManager.saveSystemConfiguration(RECAPTCHA_SECRET, clientApiKey));

        }
        log.info("performing migration from previous version, if any");
        try {
            dataMigrator.migrateEventsToCurrentVersion();
            log.info("done.");
            log.info("initialized alf.io version {} ", version);
        } catch (Exception e) {
            log.error("unable to perform data migration. Please report this issue.", e);
        }
    }
}
