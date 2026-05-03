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

import alfio.extension.exception.AlfioScriptingException;
import alfio.extension.exception.ExecutionTimeoutException;
import alfio.extension.exception.InvalidScriptException;
import alfio.extension.exception.ScriptRuntimeException;
import alfio.manager.system.AdminJobManager;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static alfio.manager.system.AdminJobExecutor.JobName.EXECUTE_EXTENSION;


// table {path, name, hash, script content, params}
// where (path, name) is unique, in our case can be:
//
// -
// -organizationId
// -organizationId-eventId

@Service
public class ScriptingExecutionService {

    public static final String EXTENSION_NAME = "extensionName";
    public static final String EXTENSION_PATH = "path";
    public static final String EXTENSION_PARAMS = "params";
    public static final String EXTENSION_CONFIGURATION_PARAMETERS = "extensionParameters";
    static final String CONNECT_EXCEPTION_MESSAGE = "Cannot connect to remote service. Please check your configuration";
    static final String DEFAULT_ERROR_MESSAGE = "Error while executing extension. Please retry.";
    private static final Logger log = LoggerFactory.getLogger(ScriptingExecutionService.class);
    private static final int EXECUTION_TIMEOUT_MS = 15_000;

    private final Supplier<Executor> executorSupplier;
    private final SimpleHttpClientApi simpleHttpClientApi;
    private final LogApi logApi = new LogApi();
    private final ExtensionUtilsApi extensionUtilsApi = new ExtensionUtilsApi();
    private final AdminJobQueueRepository adminJobQueueRepository;

    private final Cache<String, Executor> asyncExecutors = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(12))
        .removalListener((String key, Executor value, RemovalCause cause) -> {
            if (value instanceof ExecutorService service) {
                service.shutdown();
            }
        })
        .build();

    // Backward-compat shims: HashMap constructor and GSON alias.
    // The variadic wrappers for ExtensionUtils.format/computeHMAC collect
    // the JS arguments array and forward them to the proper builtins.
    private static final String JS_PRELUDE = """
        function HashMap() { return {}; }
        var GSON = {
            toJson: function(obj) { return JSON.stringify(obj); },
            fromJson: function(str) { return JSON.parse(str); }
        };
        var _origFormat = ExtensionUtils.format;
        ExtensionUtils.format = function(str) {
            return _origFormat(str, Array.prototype.slice.call(arguments, 1));
        };
        var _origHMAC = ExtensionUtils.computeHMAC;
        ExtensionUtils.computeHMAC = function(secret) {
            return _origHMAC(secret, Array.prototype.slice.call(arguments, 1));
        };
        ExtensionUtils.convertToJson = function(obj) { return JSON.stringify(obj); };
        (function() {
            function _variadicWrap(fn) {
                return function() {
                    fn(Array.prototype.slice.call(arguments).join(' '));
                };
            }
            var _methods = ['log', 'warn', 'error'];
            for (var _i = 0; _i < _methods.length; _i++) {
                console[_methods[_i]] = _variadicWrap(console[_methods[_i]]);
            }
            var _logMethods = ['warn', 'info', 'error', 'debug', 'trace'];
            for (var _i = 0; _i < _logMethods.length; _i++) {
                log[_logMethods[_i]] = _variadicWrap(log[_logMethods[_i]]);
            }
        })();
        """;

    public ScriptingExecutionService(HttpClient httpClient,
                                     AdminJobQueueRepository adminJobQueueRepository,
                                     Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
        this.adminJobQueueRepository = adminJobQueueRepository;
        this.simpleHttpClientApi = new SimpleHttpClientApi(new SimpleHttpClient(httpClient));
    }

    public <T> T executeScript(String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params, Class<T> clazz, ExtensionLogger extensionLogger) {
        return executeScriptFinally(name, scriptFetcher.get(), params, clazz, extensionLogger);
    }

    public void executeScriptAsync(String path,
                                   String name,
                                   String hash,
                                   Supplier<String> scriptFetcher,
                                   Map<String, Object> params,
                                   ExtensionLogger extensionLogger) {
        Optional.ofNullable(asyncExecutors.get(path, key -> executorSupplier.get()))
            .ifPresent(it -> it.execute(() -> {
                try {
                    executeScript(name, hash, scriptFetcher, params, Object.class, extensionLogger);
                } catch (AlfioScriptingException | IllegalStateException ex) {
                    var paramsCopy = new HashMap<>(params);
                    paramsCopy.remove(EXTENSION_CONFIGURATION_PARAMETERS);
                    Map<String, Object> metadata = Map.of(
                        EXTENSION_NAME, name,
                        EXTENSION_PATH, path,
                        EXTENSION_PARAMS, paramsCopy
                    );
                    boolean scheduled = AdminJobManager.executionScheduler(
                        EXECUTE_EXTENSION,
                        metadata,
                        ZonedDateTime.now(ClockProvider.clock()).plusSeconds(2L)
                    ).apply(adminJobQueueRepository);
                    if(!scheduled) {
                        log.warn("Cannot schedule extension {} for retry", name);
                        throw ex;
                    } else {
                        log.warn("Error while executing extension "+name + ", which has been scheduled for retry", ex);
                    }
                }
            }));
    }

    public <T> T executeScript(String name, String script, Map<String, Object> params, Class<T> clazz,  ExtensionLogger extensionLogger) {
        return executeScriptFinally(name, script, params, clazz, extensionLogger);
    }

    private <T> T executeScriptFinally(String name, String script, Map<String, Object> params, Class<T> clazz, ExtensionLogger extensionLogger) {
        if (params == null) {
            params = Collections.emptyMap();
        }

        var internalApi = new InternalApi();
        var consoleApi = new ConsoleApi(new ConsoleLogger(extensionLogger));
        var extLoggerApi = new ExtensionLoggerApi(extensionLogger);

        String paramDeclarations = buildParamDeclarations(params);
        String fullScript = paramDeclarations + "\n" + JS_PRELUDE + "\n" + script;

        var engine = Engine.builder()
            .addBuiltins(LogApi_Builtins.toBuiltins(logApi))
            .addBuiltins(ExtensionLoggerApi_Builtins.toBuiltins(extLoggerApi))
            .addBuiltins(ConsoleApi_Builtins.toBuiltins(consoleApi))
            .addBuiltins(SimpleHttpClientApi_Builtins.toBuiltins(simpleHttpClientApi))
            .addBuiltins(ExtensionUtilsApi_Builtins.toBuiltins(extensionUtilsApi))
            .addBuiltins(InternalApi_Builtins.toBuiltins(internalApi))
            .build();

        try (var runner = Runner.builder()
            .withEngine(engine)
            .withTimeoutMs(EXECUTION_TIMEOUT_MS)
            .build()) {

            runner.compileAndExec(fullScript);
            extensionLogger.logSuccess("Script executed successfully.");

            if (internalApi.getResultJson() != null && clazz != Void.class && clazz != void.class) {
                return Json.OBJECT_MAPPER.readValue(internalApi.getResultJson(), clazz);
            }
            return null;
        } catch (GuestException ex) {
            String message = ex.getMessage();
            log.warn("Runtime error in script " + name, ex);
            extensionLogger.logError(message);
            throw new ScriptRuntimeException(message, ex);
        } catch (IllegalArgumentException ex) {
            log.warn("Syntax error detected in script " + name, ex);
            extensionLogger.logError("Syntax error while executing script: " + ex.getMessage());
            throw new InvalidScriptException("Syntax error in script " + name);
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof TimeoutException) {
                throw new ExecutionTimeoutException("Script execution timeout.");
            }
            var actualException = ex.getCause() != null ? ex.getCause() : ex;
            var message = getErrorMessage(actualException);
            extensionLogger.logError("Error from script: " + message);
            throw new AlfioScriptingException(message, actualException);
        } catch (Exception ex) {
            extensionLogger.logError("Error while executing script: " + ex.getMessage());
            throw new IllegalStateException(ex);
        }
    }

    String getErrorMessage(Throwable ex) {
        if (ex.getMessage() != null) {
            return ex.getMessage();
        }
        Throwable root = ex;
        String lastMessage = root.getMessage();
        while(root.getCause() != null) {
            root = root.getCause();
            if (root instanceof ConnectException) {
                return CONNECT_EXCEPTION_MESSAGE;
            }
            if (root.getMessage() != null) {
                lastMessage = root.getMessage();
            }
        }
        return Objects.requireNonNullElse(lastMessage, DEFAULT_ERROR_MESSAGE);
    }

    private String buildParamDeclarations(Map<String, Object> params) {
        var sb = new StringBuilder();
        for (var entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String jsonValue;
            try {
                jsonValue = Json.OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                log.warn("Cannot serialize param '{}' to JSON, skipping", key, e);
                continue;
            }
            sb.append("var ").append(key).append(" = ").append(jsonValue).append(";\n");
        }
        return sb.toString();
    }

}
