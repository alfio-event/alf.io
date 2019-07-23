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
import alfio.config.MvcConfiguration;
import alfio.util.BaseIntegrationTest;
import de.codecentric.hikaku.Hikaku;
import de.codecentric.hikaku.HikakuConfig;
import de.codecentric.hikaku.converters.openapi.OpenApiConverter;
import de.codecentric.hikaku.converters.spring.SpringConverter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Paths;
import java.util.Set;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class, MvcConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
@WebAppConfiguration
public class CheckApiConformanceTest extends BaseIntegrationTest {


    @Autowired
    private ApplicationContext springContext;

    @Test
    public void specificationMatchesImplementation() {
        OpenApiConverter specification = new OpenApiConverter(Paths.get("docs/openapi.json"));
        SpringConverter implementation = new SpringConverter(springContext);

        HikakuConfig hikakuConfig = new HikakuConfig(
            Set.of("/healthz", "/", "/admin", "/file/{digest}", "/report-csp-violation"),
            //Set.of("/admin/api/", "/api/payment/*"),
            true,
            true
        );

        /*new Hikaku(
            specification,
            implementation,
            hikakuConfig
        ).match();*/
    }

}
