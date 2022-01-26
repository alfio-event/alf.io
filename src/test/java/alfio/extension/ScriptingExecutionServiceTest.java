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

import alfio.extension.exception.*;
import alfio.manager.system.AdminJobManager;
import alfio.repository.system.AdminJobQueueRepository;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

public class ScriptingExecutionServiceTest {

    private static ScriptingExecutionService scriptingExecutionService;
    private final ExtensionLogger extensionLogger = Mockito.mock(ExtensionLogger.class);

    @BeforeAll
    public static void init() {
        Supplier<Executor> executorSupplier = () -> Runnable::run;
        scriptingExecutionService = new ScriptingExecutionService(Mockito.mock(HttpClient.class), Mockito.mock(AdminJobQueueRepository.class), executorSupplier);
    }
    /**
     *
     * @param file
     * @return String content of the script
     * @throws IOException
     */
    private String getScriptContent(String file) throws IOException {
        String concatenation;
        try(var input = getClass().getResourceAsStream("/rhino-scripts/" + file)) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8));
            concatenation = String.join("\n", extensionStream)+"\n;executeScript(extensionEvent)";
        }
        return concatenation;
    }

    @Test
    void testBaseScriptExecution() throws IOException {
        String concatenation = getScriptContent("base.js");
        scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        verify(extensionLogger).logInfo("test");
    }

    @Test
    void testExecutionTimeout() {
        assertTimeoutPreemptively(Duration.ofSeconds(16L), () -> {
            try {
                String concatenation = getScriptContent("timeout.js");
                scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
                fail();
            } catch (Exception e) {
                assertTrue(e.getCause() instanceof ExecutionTimeoutException);
            }
        });
    }

    @Test
    void testOutOfBoundariesReflection() throws Exception {
        try {
            String concatenation = getScriptContent("boundariesReflection.js");
            scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        } catch (OutOfBoundariesException ex) {
            verify(extensionLogger, never()).logInfo("test");
        }
    }

    @Test
    void testOutOfBoundariesExit() throws Exception {
        try {
            String concatenation = getScriptContent("boundariesExit.js");
            scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        } catch (InvalidScriptException ex) {
            verify(extensionLogger).logError(startsWith("Syntax error while executing script:"));
        }
    }

    @Test
    void extensionThrowsError() throws Exception {
        try {
            String concatenation = getScriptContent("runtimeError.js");
            scriptingExecutionService.executeScript("name", concatenation, Map.of("extensionEvent", "test"), Void.class, extensionLogger);
        } catch(ScriptRuntimeException ex) {
            verify(extensionLogger).logError(startsWith("Error:"));
        }
    }

    @Test
    void getMessageFromException() {
        var ex = mock(RuntimeException.class);
        when(ex.getCause()).thenReturn(mock(ConnectException.class));
        assertEquals(ScriptingExecutionService.CONNECT_EXCEPTION_MESSAGE, scriptingExecutionService.getErrorMessage(ex));
        var nested = mock(RuntimeException.class);
        var root = mock(RuntimeException.class);
        when(nested.getCause()).thenReturn(root);
        when(ex.getCause()).thenReturn(nested);
        // no message present, must fall back to default
        assertEquals(ScriptingExecutionService.DEFAULT_ERROR_MESSAGE, scriptingExecutionService.getErrorMessage(ex));

        // nested returns a message, so it should be used
        when(nested.getMessage()).thenReturn("error message");
        assertEquals("error message", scriptingExecutionService.getErrorMessage(ex));

        when(root.getMessage()).thenReturn("root message");
        assertEquals("root message", scriptingExecutionService.getErrorMessage(ex));
    }
}