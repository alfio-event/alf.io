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
import alfio.extension.exception.InvalidScriptException;
import alfio.extension.exception.OutOfBoundariesException;
import alfio.extension.exception.ScriptRuntimeException;
import alfio.extension.support.SandboxContextFactory;
import alfio.manager.system.AdminJobManager;
import alfio.repository.system.AdminJobQueueRepository;
import alfio.util.ClockProvider;
import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.log4j.Log4j2;
import org.mozilla.javascript.*;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.manager.system.AdminJobExecutor.JobName.EXECUTE_EXTENSION;


// table {path, name, hash, script content, params}
// where (path, name) is unique, in our case can be:
//
// -
// -organizationId
// -organizationId-eventId

@Service
@Log4j2
public class ScriptingExecutionService {

    public static final String EXTENSION_NAME = "extensionName";
    public static final String EXTENSION_PATH = "path";
    public static final String EXTENSION_PARAMS = "params";
    public static final String EXTENSION_CONFIGURATION_PARAMETERS = "extensionParameters";
    static final String CONNECT_EXCEPTION_MESSAGE = "Cannot connect to remote service. Please check your configuration";
    static final String DEFAULT_ERROR_MESSAGE = "Error while executing extension. Please retry.";

    private final Supplier<Executor> executorSupplier;
    private final ScriptableObject sealedScope;
    private final AdminJobQueueRepository adminJobQueueRepository;

    private final Cache<String, Executor> asyncExecutors = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(12))
        .removalListener((String key, Executor value, RemovalCause cause) -> {
            if (value instanceof ExecutorService) {
                ((ExecutorService) value).shutdown();
            }
        })
        .build();

    static {
        ContextFactory.initGlobal(new SandboxContextFactory());
    }


    public ScriptingExecutionService(HttpClient httpClient,
                                     AdminJobQueueRepository adminJobQueueRepository,
                                     Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
        this.adminJobQueueRepository = adminJobQueueRepository;
        var simpleHttpClient = new SimpleHttpClient(httpClient);
        Context cx = ContextFactory.getGlobal().enterContext();
        try {
            sealedScope = cx.initSafeStandardObjects(null, true);
            sealedScope.put("log", sealedScope, log);
            sealedScope.put("GSON", sealedScope, Json.GSON);
            sealedScope.put("JSON", sealedScope, new NativeJavaClass(sealedScope, JSON.class));
            sealedScope.put("simpleHttpClient", sealedScope, simpleHttpClient);
            sealedScope.put("HashMap", sealedScope, new NativeJavaClass(sealedScope, HashMap.class));
            sealedScope.put("ExtensionUtils", sealedScope, new NativeJavaClass(sealedScope, ExtensionUtils.class));
        } finally {
            Context.exit();
        }
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
                    // we got an error while executing the script. We must now re-schedule the script to be executed again
                    // at a later time
                    var paramsCopy = new HashMap<>(params);
                    // do not persist extension parameters because they could contain sensitive information
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
                        // throw exception only if we can't schedule the extension for later execution
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

    public static class JavaClassInterop {

        private final Map<String, Class<?>> mapping;
        private final Scriptable scope;

        JavaClassInterop(Map<String, Class<?>> mapping, Scriptable scope) {
            this.mapping = mapping;
            this.scope = scope;
        }

        public NativeJavaClass type(String clazz) {
            if (mapping.containsKey(clazz)) {
                return new NativeJavaClass(scope, mapping.get(clazz));
            } else {
                throw new IllegalArgumentException("Type "+clazz+" is not recognized");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T executeScriptFinally(String name, String script, Map<String, Object> params, Class<T> clazz,  ExtensionLogger extensionLogger) {
        Context cx = Context.enter();
        try {
            if(params == null) {
                params = Collections.emptyMap();
            }

            Scriptable scope = cx.newObject(sealedScope);
            scope.setPrototype(sealedScope);
            scope.setParentScope(null);
            scope.put("extensionLogger", scope, extensionLogger);
            scope.put("console", scope, new ConsoleLogger(extensionLogger));

            // retrocompatibility
            scope.put("Java", scope, new JavaClassInterop(Map.of("alfio.model.CustomerName", alfio.model.CustomerName.class), scope));

            scope.put("returnClass", scope, clazz);

            for (var entry : params.entrySet()) {
                var value = entry.getValue();
                if(entry.getKey().equals(EXTENSION_CONFIGURATION_PARAMETERS)) {
                    scope.put(entry.getKey(), scope, convertExtensionParameters(scope, value));
                } else {
                    scope.put(entry.getKey(), scope, Context.javaToJS(value, scope));
                }
            }
            Object res;
            res = cx.evaluateString(scope, script, name, 1, null);
            extensionLogger.logSuccess("Script executed successfully.");
            if (res instanceof NativeJavaObject) {
                NativeJavaObject nativeRes = (NativeJavaObject) res;
                return (T) nativeRes.unwrap();
            } else if(clazz.isInstance(res)) {
                return (T) res;
            } else {
                return null;
            }
        } catch (EcmaError ex) {
            log.warn("Syntax error detected in script " + name, ex);
            extensionLogger.logError("Syntax error while executing script: " + ex.getMessage() + "(" + ex.lineNumber() + ":" + ex.columnNumber() + ")");
            throw new InvalidScriptException("Syntax error in script " + name);
        } catch (WrappedException ex) {
            var actualException = ex.getWrappedException();
            var message = getErrorMessage(actualException);
            extensionLogger.logError("Error from script: " + message);
            throw new AlfioScriptingException(message, actualException);
        } catch (JavaScriptException ex) {
            String message;
            if (ex.getValue() != null) {
                message = ex.details();
            } else {
                message = ex.getMessage();
            }
            extensionLogger.logError(message);
            throw new ScriptRuntimeException(message, ex);
        } catch (OutOfBoundariesException ex) {
            throw ex;
        } catch (Exception ex) { //
            extensionLogger.logError("Error while executing script: " + ex.getMessage());
            throw new IllegalStateException(ex);
        } finally {
            Context.exit();
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

    private Object convertExtensionParameters(Scriptable context, Object extensionParameters) {
        return ((Map<?, ?>) extensionParameters).entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), ScriptRuntime.toObject(context, entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
