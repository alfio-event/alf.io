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
package alfio;

import alfio.config.support.PlatformProvider;
import alfio.manager.FileDownloadManager;
import alfio.test.util.IntegrationTestUtil;
import alfio.util.BaseIntegrationTest;
import com.opentable.db.postgres.embedded.EmbeddedPostgres;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;


import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;



@Configuration
public class TestConfiguration {

    private EmbeddedPostgres postgres;

    private final String POSTGRES_USERNAME = "postgres";
    private final String POSTGRES_PASSWORD = "postgres";
    private final String POSTGRES_DB = "postgres";

    @Bean
    @Profile("!travis")
    public PlatformProvider getCloudProvider(EmbeddedPostgres postgres) {
        IntegrationTestUtil.generateDBConfig(postgres.getJdbcUrl(POSTGRES_USERNAME, POSTGRES_DB), POSTGRES_USERNAME, POSTGRES_PASSWORD)
            .forEach(System::setProperty);
        return PlatformProvider.DEFAULT;
    }

    @Bean
    @Profile("!travis")
    public DataSource getDataSource(EmbeddedPostgres postgres) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(postgres.getJdbcUrl(POSTGRES_USERNAME, POSTGRES_DB));
        dataSource.setUsername(POSTGRES_USERNAME);
        dataSource.setPassword(POSTGRES_PASSWORD);
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setMaximumPoolSize(5);
        return dataSource;
    }

    @Bean
    @Profile("!travis")
    public EmbeddedPostgres postgres() throws IOException {
        Path pgsqlPath = Paths.get(".", "alfio-itest");
        Files.createDirectories(pgsqlPath);
        Path tmpDataDir = Files.createTempDirectory(pgsqlPath, "alfio-data");
        postgres = EmbeddedPostgres.builder().setDataDirectory(tmpDataDir).start();
        return postgres;
    }

    @PreDestroy
    public void shutdown() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }


    @Bean
    public static PropertyPlaceholderConfigurer propConfig() {
        Properties properties = new Properties();
        properties.put("alfio.version", "1.9-SNAPSHOT");
        properties.put("alfio.build-ts", ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1).toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        properties.list(pw);
        pw.flush();
        PropertyPlaceholderConfigurer ppc =  new PropertyPlaceholderConfigurer();
        ppc.setLocation(new ByteArrayResource(out.toByteArray()));
        return ppc;
    }

    @Bean
    public FileDownloadManager fileDownloadManager() {
        return new FileDownloadManager() {
            @Override
            public DownloadedFile downloadFile(String url) {
                return new DownloadedFile(BaseIntegrationTest.ONE_PIXEL_BLACK_GIF, "test", "image/gif");
            }
        };
    }
}
