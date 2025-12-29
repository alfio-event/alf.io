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
package alfio.controller.api;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import alfio.util.Json;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.output.MarkdownRender;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.utils.Constants;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {
    DataSourceConfiguration.class,
    TestConfiguration.class,
    ControllerConfiguration.class,
    TestCheckRestApiStability.DisableSecurity.class,
    SpringDocConfiguration.class,
    SpringDocConfigProperties.class,
    SpringDocWebMvcConfiguration.class
})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
class TestCheckRestApiStability {

    private static final String DESCRIPTOR_JSON_PATH = "src/test/resources/api/descriptor.json";
    @Autowired
    private MockMvc mockMvc;

    private final boolean updateDescriptor = false; // change to true to regenerate the file

    @Test
    void checkRestApiStability() throws Exception {

        var mvcResult = this.mockMvc.perform(get(Constants.DEFAULT_API_DOCS_URL))
            .andExpect(status().isOk())
            .andReturn();

        var response = mvcResult.getResponse();
        var content = response.getContentAsString();
        // for some reason we get a quoted base64 JSON: "ey..."
        var descriptor = Base64.getDecoder().decode(content.substring(1, content.length() - 1));

        // for generating the result
        if (updateDescriptor) {
            try (var writer = Files.newBufferedWriter(Paths.get(DESCRIPTOR_JSON_PATH), StandardCharsets.UTF_8)) {
                var formattedDescriptor = Json.OBJECT_MAPPER.readTree(descriptor).toPrettyString();
                writer.write(formattedDescriptor);
            }
        }

        var referenceDescriptor = IOUtils.toString(new FileReader(DESCRIPTOR_JSON_PATH));
        var currentDescriptor = IOUtils.toString(descriptor, StandardCharsets.UTF_8.toString());
        var compareResult = OpenApiCompare.fromContents(referenceDescriptor, currentDescriptor);
        if (compareResult.isDifferent()) {
            var out = new ByteArrayOutputStream();
            new MarkdownRender().render(compareResult, new OutputStreamWriter(out));
            Assertions.fail(out.toString(StandardCharsets.UTF_8));
        }
    }


    @EnableWebSecurity
    @Configuration
    public static class DisableSecurity {

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.requestMatchers("/**").permitAll()).build();
        }
    }
}
