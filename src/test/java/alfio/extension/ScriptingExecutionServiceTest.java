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
package alfio.extension;

import alfio.TestConfiguration;
import alfio.config.DataSourceConfiguration;
import alfio.config.Initializer;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {DataSourceConfiguration.class, TestConfiguration.class})
@ActiveProfiles({Initializer.PROFILE_DEV, Initializer.PROFILE_DISABLE_JOBS, Initializer.PROFILE_INTEGRATION_TEST})
@Transactional
public class ScriptingExecutionServiceTest {

    @Autowired
    private ScriptingExecutionService scriptingExecutionService;
    private ExtensionLogger extensionLogger = Mockito.mock(ExtensionLogger.class);

    @Test
    public void testBaseScriptExecution() throws IOException {
        String concatenation;
        try(var input = getClass().getResourceAsStream("/rhino-scripts/base.js")) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(input, StandardCharsets.UTF_8));
            concatenation = String.join("\n", extensionStream)+"\n;GSON.fromJson(JSON.stringify(executeScript(extensionEvent)), returnClass);";
        }
        scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        Mockito.verify(extensionLogger).logInfo(eq("test"));
    }

}