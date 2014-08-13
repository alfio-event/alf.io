/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.interceptor;

import io.bagarino.model.system.ConfigurationKeys;
import io.bagarino.repository.system.ConfigurationRepository;
import io.bagarino.repository.user.RoleRepository;
import io.bagarino.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ConfigurationStatusChecker implements ApplicationListener<ContextRefreshedEvent> {

    private final ConfigurationRepository configurationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public ConfigurationStatusChecker(ConfigurationRepository configurationRepository,
                                      UserRepository userRepository,
                                      RoleRepository roleRepository,
                                      PasswordEncoder passwordEncoder) {
        this.configurationRepository = configurationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        boolean initCompleted;
        try {
            initCompleted = Boolean.parseBoolean(configurationRepository.findByKey(ConfigurationKeys.INIT_COMPLETED).getValue());
        } catch (EmptyResultDataAccessException e) {
            initCompleted = false;
        }
        if (!initCompleted) {
            String adminPassword = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
            String logMessage = "*******************************************************\n"+
                                "   This is the first time you're running bagarino\n"+
                                "   a new Admin Password has been generated:\n"+
                                "   " + adminPassword+"\n"+
                                "*******************************************************";
            userRepository.create("admin", passwordEncoder.encode(adminPassword), "The", "Administrator", "admin@localhost", true);
            roleRepository.create("admin", "ROLE_ADMIN");
            System.out.println(logMessage);
            configurationRepository.insert(ConfigurationKeys.INIT_COMPLETED, "true");
        }
    }
}
