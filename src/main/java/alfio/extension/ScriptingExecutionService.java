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
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.script.*;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


//
// table {path, name, hash, script content, params}
// where path is unique, in our case can be:
//
// /name
// /organization/name
// /organization/event/name
// /organization/event/ticket_category/name
//
// for async execution -> for each path make a queue

@Service
@Log4j2
public class ScriptingExecutionService {

    private final static Compilable engine = (Compilable) new ScriptEngineManager().getEngineByName("nashorn");
    private final Cache<String, CompiledScript> compiledScriptCache = Caffeine.newBuilder()
        .expireAfterAccess(12, TimeUnit.HOURS)
        .build();
    private final Cache<String, ExecutorService> asyncExecutors = Caffeine.newBuilder()
        .expireAfterAccess(12, TimeUnit.HOURS)
        .build();

    public <T> T executeScript(String path, String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params, Class<T> clazz) {
        CompiledScript compiledScript = compiledScriptCache.get(hash, (key) -> {
            try {
                return engine.compile(scriptFetcher.get());
            } catch (ScriptException se) {
                log.warn("Was not able to compile script " + name, se);
                throw new IllegalStateException(se);
            }
        });
        return executeScript(name, compiledScript, params, clazz);
    }

    public void executeScriptAsync(String path, String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params) {
        Optional.ofNullable(asyncExecutors.get(path, (key) -> Executors.newSingleThreadExecutor()))
            .ifPresent(it -> it.submit(() -> {
               executeScript(path, name, hash, scriptFetcher, params, Object.class);
            }));
    }


    public static <T> T executeScript(String name, String script, Map<String, Object> params, Class<T> clazz) {
        try {
            CompiledScript compiledScript = engine.compile(script);
            return executeScript(name, compiledScript, params, clazz);
        } catch (ScriptException se) {
            log.warn("Was not able to compile script", se);
            throw new IllegalStateException(se);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T executeScript(String name, CompiledScript script, Map<String, Object> params, Class<T> clazz) {
        try {
            if(params == null) {
                params = Collections.emptyMap();
            }
            ScriptContext newContext = new SimpleScriptContext();
            Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
            engineScope.put("log", log);
            engineScope.put("GSON", Json.GSON);
            engineScope.put("restTemplate", new RestTemplate());
            engineScope.put("returnClass", clazz);
            engineScope.putAll(params);
            return (T) script.eval(newContext);
        } catch (ScriptException ex) {
            log.warn("Error while executing script " + name, ex);
            throw new IllegalStateException(ex);
        }
    }
}
