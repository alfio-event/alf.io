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

import alfio.extension.exception.ExecutionTimeoutException;
import alfio.extension.exception.OutOfBoundariesException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.eq;

public class ScriptingExecutionServiceTest {
    private Supplier<Executor> executorSupplier = () -> Runnable::run;
    private ScriptingExecutionService scriptingExecutionService = new ScriptingExecutionService(Mockito.mock(HttpClient.class), executorSupplier);

    private ExtensionLogger extensionLogger = Mockito.mock(ExtensionLogger.class);

    /**
     *
     * @param file
     * @return String content of the script
     * @throws IOException
     */
    private String getScriptContent(String file) throws IOException {
        String concatenation;
        try(var input = getClass().getResourceAsStream("/rhino-scripts/" + file)) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(input, StandardCharsets.UTF_8));
            concatenation = String.join("\n", extensionStream)+"\n;executeScript(extensionEvent)";
        }
        return concatenation;
    }

    @Test
     void testBaseScriptExecution() throws IOException {
        String concatenation = getScriptContent("base.js");
        scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        Mockito.verify(extensionLogger).logInfo(eq("test"));
    }

    @Test
     void testExecutionTimeout()  {
        assertTimeout(Duration.ofSeconds(11L), () -> assertThrows(ExecutionTimeoutException.class, () -> {
                String concatenation = getScriptContent("timeout.js");
                scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
            }
        ));
    }

    @Test
     void testOutOfBoundaries()  {
        assertThrows(OutOfBoundariesException.class, () -> {
            String concatenation = getScriptContent("boundaries.js");
            scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
            Mockito.verify(extensionLogger).logInfo(eq("test"));
        });
    }
}