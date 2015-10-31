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

import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Properties;

@Configuration
public class TestConfiguration {
    @Bean
    public static PropertyPlaceholderConfigurer propConfig() {
        Properties properties = new Properties();
        properties.put("alfio.version", "1.5-SNAPSHOT");
        properties.put("alfio.build-ts", ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1).toString());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(out);
        properties.list(pw);
        pw.flush();
        PropertyPlaceholderConfigurer ppc =  new PropertyPlaceholderConfigurer();
        ppc.setLocation(new ByteArrayResource(out.toByteArray()));
        return ppc;
    }
}
