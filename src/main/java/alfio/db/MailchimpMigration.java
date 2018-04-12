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
package alfio.db;

import alfio.extension.Extension;
import alfio.extension.ExtensionService;
import alfio.extension.ScriptingExecutionService;
import alfio.repository.EventRepository;
import alfio.repository.ExtensionLogRepository;
import alfio.repository.ExtensionRepository;
import ch.digitalfondue.npjt.Bind;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import ch.digitalfondue.npjt.Query;
import ch.digitalfondue.npjt.QueryFactory;
import org.flywaydb.core.api.migration.spring.BaseSpringJdbcMigration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static alfio.util.OptionalWrapper.optionally;

public class MailchimpMigration extends BaseSpringJdbcMigration {


    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        Integer enabledCount = jdbcTemplate.queryForObject("select count(*) from plugin_configuration where plugin_id = 'alfio.mailchimp' and conf_name = 'enabled' and conf_value = 'true'", Integer.class);
        if (enabledCount == 0) {
            return;
        }


        //extract sql dialect from the directory location: from "classpath:alfio/db/HSQLDB" to hsqldb
        String dialect = flywayConfiguration.getLocations()[0].split("\\/db\\/")[1].toLowerCase(Locale.ROOT);

        QueryFactory queryFactory = new QueryFactory(dialect, jdbcTemplate);

        EventRepository eventRepository = queryFactory.from(EventRepository.class);
        ExtensionRepository extensionRepository = queryFactory.from(ExtensionRepository.class);
        ExtensionLogRepository extensionLogRepository = queryFactory.from(ExtensionLogRepository.class);
        PluginRepository pluginRepository = queryFactory.from(PluginRepository.class);
        ExtensionService extensionService = new ExtensionService(new ScriptingExecutionService(), extensionRepository, extensionLogRepository, new DataSourceTransactionManager(jdbcTemplate.getDataSource()));

        extensionService.createOrUpdate(null, null, new Extension("-", "mailchimp", getMailChimpScript(), true));

        int extensionId = extensionRepository.getExtensionIdFor("-", "mailchimp");
        int apiKeyId = pluginRepository.getConfigurationMetadataIdFor(extensionId, "apiKey", "EVENT");
        int listIdId = pluginRepository.getConfigurationMetadataIdFor(extensionId, "listId", "EVENT");


        List<ConfValue> confValues = pluginRepository.findAllMailChimpConfigurationValues();

        for (ConfValue cv : confValues) {
            if(cv.value != null) {
                optionally(() ->jdbcTemplate.queryForObject("select org_id from event where id = "+cv.eventId, Integer.class))
                    .ifPresent(orgId -> extensionRepository.insertSettingValue("apiKey".equals(cv.name) ? apiKeyId : listIdId, "-" + orgId + "-" + cv.eventId, cv.value));

            }
        }
    }

    private static String getMailChimpScript() {
        try (InputStream is = new ClassPathResource("/alfio/extension/mailchimp.js").getInputStream()){
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read mailchimp file", e);
        }
    }

    public interface PluginRepository {
        @Query("select conf_name, conf_value, event_id from plugin_configuration where plugin_id = 'alfio.mailchimp' and conf_name in ('apiKey', 'listId')")
        List<ConfValue> findAllMailChimpConfigurationValues();


        @Query("select ecm_id from extension_configuration_metadata where ecm_es_id_fk = :extensionId and ecm_name = :name and ecm_configuration_level = :confLevel")
        int getConfigurationMetadataIdFor(@Bind("extensionId") int extensionId, @Bind("name") String name, @Bind("confLevel") String confLevel);
    }

    public static class ConfValue {
        String name;
        String value;
        int eventId;

        public ConfValue(@Column("conf_name") String name,
                         @Column("conf_value") String value,
                         @Column("event_id") int eventId) {
            this.name = name;
            this.value = value;
            this.eventId = eventId;
        }
    }
}
