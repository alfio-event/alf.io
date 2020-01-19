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

import alfio.util.Json;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import lombok.extern.log4j.Log4j2;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaClass;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;


// table {path, name, hash, script content, params}
// where (path, name) is unique, in our case can be:
//
// -
// -organizationId
// -organizationId-eventId

@Service
@Log4j2
public class ScriptingExecutionService {

    private final SimpleHttpClient simpleHttpClient;
    private final Supplier<Executor> executorSupplier;
    private final Scriptable sealedScope;

    private final Cache<String, Executor> asyncExecutors = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(12))
        .removalListener((String key, Executor value, RemovalCause cause) -> {
            if (value instanceof ExecutorService) {
                ((ExecutorService) value).shutdown();
            }
        })
        .build();

    public ScriptingExecutionService(HttpClient httpClient, Supplier<Executor> executorSupplier) {
        this.simpleHttpClient = new SimpleHttpClient(httpClient);
        this.executorSupplier = executorSupplier;
        Context cx = Context.enter();
        try {
            sealedScope = cx.initSafeStandardObjects();
            sealedScope.put("log", sealedScope, log);
            sealedScope.put("GSON", sealedScope, Json.GSON);
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

    public void executeScriptAsync(String path, String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params,  ExtensionLogger extensionLogger) {
        Optional.ofNullable(asyncExecutors.get(path, key -> executorSupplier.get()))
            .ifPresent(it -> it.execute(() -> executeScript(name, hash, scriptFetcher, params, Object.class, extensionLogger)));
    }


    public <T> T executeScript(String name, String script, Map<String, Object> params, Class<T> clazz,  ExtensionLogger extensionLogger) {
        return executeScriptFinally(name, script, params, clazz, extensionLogger);
    }

    public static class JavaClassInterop {

        private final Map<String, Class> mapping;
        private final Scriptable scope;

        JavaClassInterop(Map<String, Class> mapping, Scriptable scope) {
            this.mapping = mapping;
            this.scope = scope;
        }

        public NativeJavaClass type(String clazz) {
            if (mapping.containsKey(clazz)) {
                return new NativeJavaClass(scope, mapping.get(clazz));
            } else {
                throw new IllegalArgumentException("");
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

            // retrocompatibility
            scope.put("Java", scope, new JavaClassInterop(Map.of("alfio.model.CustomerName", alfio.model.CustomerName.class), scope));
            //

            scope.put("returnClass", scope, clazz);

            for (var entry : params.entrySet()) {
                scope.put(entry.getKey(), scope, entry.getValue());
            }

            Object res = cx.evaluateString(scope, script, name, 1, null);
            extensionLogger.logSuccess("Script executed successfully");
            if (res instanceof NativeJavaObject) {
                NativeJavaObject nativeRes = (NativeJavaObject) res;
                return (T) nativeRes.unwrap();
            } else {
                return null;
            }
        } catch (Throwable ex) { //
            log.warn("Error while executing script " + name + ":", ex);
            extensionLogger.logError("Error while executing script: " + ex.getMessage());
            throw new IllegalStateException(ex);
        } finally {
            Context.exit();
        }
    }
}
