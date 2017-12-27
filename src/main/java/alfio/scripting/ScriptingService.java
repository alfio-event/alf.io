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

import alfio.model.ScriptSupport;
import alfio.repository.ScriptRepository;
import alfio.util.Json;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
@AllArgsConstructor
public class ScriptingService {

    private final ScriptingExecutionService scriptingExecutionService;

    private final ScriptRepository scriptRepository;

    @Transactional
    public void createOrUpdate(Script script) {
        String hash = DigestUtils.sha256Hex(script.getScript());
        ScriptMetadata scriptMetadata = ScriptingExecutionService.executeScript(
            script.getName(),
            script.getScript() + "\n;return getScriptMetadata();",
            script.getConfiguration());


        if(scriptRepository.hasPath(script.getPath()) > 0) {
            scriptRepository.deleteEventsForPath(script.getPath());
            scriptRepository.deleteScriptForPath(script.getPath());
        }

        scriptRepository.insert(script.getPath(), script.getName(), hash, script.isEnabled(), scriptMetadata.async, script.getScript(), Json.toJson(script.getConfiguration()));
        for(String event : scriptMetadata.events) {
            scriptRepository.insert(script.getPath(), event);
        }
    }

    @Transactional
    public void toggle(String path, boolean status) {
        scriptRepository.toggle(path, status);
    }

    @Transactional
    public void delete(String path) {
        scriptRepository.deleteEventsForPath(path);
        scriptRepository.deleteScriptForPath(path);
    }

    public String getScript(String path) {
        return scriptRepository.getScript(path);
    }

    public <T> T executeScriptsForEvent(String event, String basePath, Map<String, Object> payload) {
        List<Triple<String, String, String>> activePaths = getActiveScriptsForEvent(event, basePath);
        T res = null;
        Map<String, Object> input = new HashMap<>(payload);
        input.put("event", event);
        for(Triple<String, String, String> activePath : activePaths) {
            String path = activePath.getLeft();
            res = scriptingExecutionService.executeScript(activePath.getLeft(), activePath.getMiddle(), activePath.getRight(),
                () -> getScript(path)+"\n;return executeScript(event);", input);
            input.put("output", res);
        }
        return res;
    }

    private List<Triple<String, String, String>> getActiveScriptsForEvent(String event, String basePath) {
        return Collections.emptyList();
    }

    public List<ScriptSupport> listAll() {
        return scriptRepository.listAll();
    }
}
