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

package alfio.scripting;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import javax.script.*;
import java.util.Map;
import java.util.concurrent.*;
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

    private final static Compilable engine = (Compilable) new ScriptEngineManager().getEngineByName("javascript");
    private final Cache<String, CompiledScript> compiledScriptCache = Caffeine.newBuilder()
        .expireAfterAccess(12, TimeUnit.HOURS)
        .build();
    private final Cache<String, ExecutorService> asyncExecutors = Caffeine.newBuilder()
        .expireAfterAccess(12, TimeUnit.HOURS)
        .build();

    public <T> T executeScript(String path, String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params) {
        CompiledScript compiledScript = compiledScriptCache.get(hash, (key) -> {
            try {
                return engine.compile(scriptFetcher.get());
            } catch (ScriptException se) {
                log.warn("Was not able to compile script " + name, se);
                throw new IllegalStateException(se);
            }
        });
        return (T) executeScript(name, compiledScript, params);
    }

    public void executeScriptAsync(String path, String name, String hash, Supplier<String> scriptFetcher, Map<String, Object> params) {
        asyncExecutors.get(path, (key) -> Executors.newSingleThreadExecutor()).submit(() -> {
           executeScript(path, name, hash, scriptFetcher, params);
        });
    }

    public static <T> T executeScript(String name, String script, Map<String, Object> params) {
        try {
            CompiledScript compiledScript = engine.compile(script);
            return (T) executeScript(name, compiledScript, params);
        } catch (ScriptException se) {
            log.warn("Was not able to compile script", se);
            throw new IllegalStateException(se);
        }
    }

    private static Object executeScript(String name, CompiledScript script, Map<String, Object> params) {
        try {
            ScriptContext newContext = new SimpleScriptContext();
            Bindings engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE);
            engineScope.putAll(params);
            return script.eval(newContext);
        } catch (ScriptException ex) {
            log.warn("Error while executing script " + name, ex);
            throw new IllegalStateException(ex);
        }
    }
}
