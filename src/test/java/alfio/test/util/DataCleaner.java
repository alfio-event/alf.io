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
package alfio.test.util;

import alfio.config.authentication.support.APITokenAuthentication;
import alfio.manager.OrganizationDeleter;
import alfio.util.RefreshableDataSource;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static alfio.config.authentication.support.AuthenticationConstants.SYSTEM_API_CLIENT;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataCleaner implements AfterEachCallback, BeforeEachCallback {

    private static final Logger log = LoggerFactory.getLogger(DataCleaner.class);
    private final Set<String> initialConfiguration = new HashSet<>();

    @Override
    public void afterEach(ExtensionContext context) {
        if (hasDataSource(context)) {
            // test succeeded
            var applicationContext = SpringExtension.getApplicationContext(context);
            var jdbc = applicationContext.getBean(NamedParameterJdbcTemplate.class);
            try {
                // delete configuration
                assertTrue(jdbc.update("delete from configuration where c_key not in (:keys)", Map.of("keys", initialConfiguration)) >= 0);
                assertTrue(jdbc.update("delete from configuration_organization", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from configuration_event", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from extension_event", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from extension_configuration_metadata_value", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from extension_configuration_metadata", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from extension_log", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from extension_support", Map.of()) >= 0);
                assertTrue(jdbc.update("delete from admin_job_queue", Map.of()) >= 0);
                // delete organization
                var organizationDeleter = applicationContext.getBean(OrganizationDeleter.class);
                jdbc.queryForList("select id from organization", Map.of(), Integer.class)
                    .forEach(orgId -> organizationDeleter.deleteOrganization(orgId, new APITokenAuthentication("TEST", "", List.of(new SimpleGrantedAuthority("ROLE_" + SYSTEM_API_CLIENT)))));
                assertTrue(jdbc.queryForList("select id from organization", Map.of(), Integer.class).isEmpty());
                jdbc.update("delete from user_profile", Map.of());
                jdbc.update("delete from ba_user", Map.of());
            } catch (UncategorizedSQLException e) {
                log.warn("cannot delete data. Connection was already aborted?", e);
            }
        }
        getDataSource(context).refresh();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        if (hasDataSource(context)) {
            var jdbc = SpringExtension.getApplicationContext(context).getBean(NamedParameterJdbcTemplate.class);
            initialConfiguration.addAll(jdbc.queryForList("select c_key from configuration", Map.of(), String.class));
        }
    }

    private boolean hasDataSource(ExtensionContext extensionContext) {
        var applicationContext = SpringExtension.getApplicationContext(extensionContext);
        return applicationContext.getBeanNamesForType(RefreshableDataSource.class).length > 0
            && applicationContext.getBeanNamesForType(NamedParameterJdbcTemplate.class).length > 0;
    }

    private RefreshableDataSource getDataSource(ExtensionContext extensionContext) {
        return SpringExtension.getApplicationContext(extensionContext).getBean(RefreshableDataSource.class);
    }
}
