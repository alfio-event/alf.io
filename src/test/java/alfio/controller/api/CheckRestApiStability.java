package alfio.controller.api;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import jakarta.json.Json;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.FileReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, ControllerConfiguration.class, CheckRestApiStability.SwaggerConfig.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
public class CheckRestApiStability {

    @Autowired
    private MockMvc mockMvc;

    private boolean updateDescriptor = false; // change to true and copy the file

    @Test
    @Disabled
    void checkRestApiStability() throws Exception {
        var mvcResult = this.mockMvc.perform(get("/v2/api-docs")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

        var response = mvcResult.getResponse();
        var descriptor = response.getContentAsString();

        // for generating the result
        if (updateDescriptor) {
            try (var writer = Files.newBufferedWriter(Paths.get("descriptor.json"), StandardCharsets.UTF_8)) {
                writer.write(descriptor);
            }
            return;
        }

        var referenceDescriptor = Json.createReader(new FileReader("src/test/resources/api/descriptor.json")).readValue();
        var currentDescriptor = Json.createReader(new StringReader(descriptor)).readValue();

        var diff = Json.createDiff(referenceDescriptor.asJsonObject(), currentDescriptor.asJsonObject());
        Assertions.assertEquals(Json.createArrayBuilder().build(), diff.toJsonArray()); //TODO: it's currently not stable, we need to understand why
    }

    @EnableSwagger2
    @EnableWebSecurity
    public static class SwaggerConfig extends WebSecurityConfigurerAdapter {
        public Docket restApi() {
            return new Docket(DocumentationType.OAS_30)
                .select()
                .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.ant("/admin/api/**").or(PathSelectors.ant("/api/**")))
                .build();
        }

        @Override
        protected void configure(HttpSecurity http) throws Exception {
            http.authorizeRequests().antMatchers("/**").permitAll();
        }

    }
}
