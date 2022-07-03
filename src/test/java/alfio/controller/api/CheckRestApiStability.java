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
import jakarta.json.Json;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springdoc.core.*;
import org.springdoc.webmvc.core.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    @Autowired
    private MockMvc mockMvc;

    private boolean updateDescriptor = false; // change to true and copy the file

    @Test
    void checkRestApiStability() throws Exception {

        var mvcResult = this.mockMvc.perform(get(Constants.DEFAULT_API_DOCS_URL))
            .andExpect(status().isOk())
            .andReturn();

        var response = mvcResult.getResponse();
        var descriptor = response.getContentAsString();

        // for generating the result
        if (updateDescriptor) {
            try (var writer = Files.newBufferedWriter(Paths.get("descriptor.json"), StandardCharsets.UTF_8)) {
                writer.write(descriptor);
            }
        }

        var referenceDescriptor = Json.createReader(new FileReader("src/test/resources/api/descriptor.json")).readValue();
        var currentDescriptor = Json.createReader(new StringReader(descriptor)).readValue();

        var diff = Json.createDiff(referenceDescriptor.asJsonObject(), currentDescriptor.asJsonObject());
        Assertions.assertEquals(Json.createArrayBuilder().build(), diff.toJsonArray());
    }


    @EnableWebSecurity
    @Configuration
    public static class DisableSecurity extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests().antMatchers("/**").permitAll();
        }
    }
}
