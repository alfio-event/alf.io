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

import alfio.model.ExtensionSupport;
import alfio.model.ExtensionSupport.ScriptPathNameHash;
import alfio.repository.ExtensionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Log4j2
@AllArgsConstructor
public class ExtensionService {

    private final ScriptingExecutionService scriptingExecutionService;

    private final ExtensionRepository extensionRepository;

    @Transactional
    public void createOrUpdate(String previousPath, String previousName, Extension script) {
        Validate.notBlank(script.getName(), "Name is mandatory");
        String hash = DigestUtils.sha256Hex(script.getScript());
        ExtensionMetadata extensionMetadata = ScriptingExecutionService.executeScript(
            script.getName(),
            script.getScript() + "\n;GSON.fromJson(JSON.stringify(getScriptMetadata()), returnClass);", //<- ugly hack, but the interop java<->js is simpler that way...
            Collections.emptyMap(),
            ExtensionMetadata.class);

        if (previousPath != null && previousName != null && extensionRepository.hasPath(previousPath, previousName) > 0) {
            extensionRepository.deleteEventsForPath(previousPath, previousName);
            extensionRepository.deleteScriptForPath(previousPath, previousName);
        }

        extensionRepository.insert(script.getPath(), script.getName(), hash, script.isEnabled(), extensionMetadata.async, script.getScript());
        for (String event : extensionMetadata.events) {
            extensionRepository.insertEvent(script.getPath(), script.getName(), event);
        }
    }

    @Transactional
    public void toggle(String path, String name, boolean status) {
        extensionRepository.toggle(path, name, status);
    }

    @Transactional
    public void delete(String path, String name) {
        extensionRepository.deleteEventsForPath(path, name);
        extensionRepository.deleteScriptForPath(path, name);
    }

    public String getScript(String path, String name) {
        return extensionRepository.getScript(path, name);
    }

    public Optional<ExtensionSupport> getSingle(String path, String name) {
        return extensionRepository.getSingle(path, name);
    }

    public <T> T executeScriptsForEvent(String event, String basePath, Map<String, Object> payload, Class<T> clazz) {
        List<ScriptPathNameHash> activePaths = getActiveScriptsForEvent(event, basePath, false);
        T res = null;
        Map<String, Object> input = new HashMap<>(payload);
        input.put("extensionEvent", event);
        for (ScriptPathNameHash activePath : activePaths) {
            String path = activePath.getPath();
            String name = activePath.getName();
            res = scriptingExecutionService.executeScript(name, activePath.getHash(),
                () -> getScript(path, name)+"\n;GSON.fromJson(JSON.stringify(executeScript(extensionEvent)), returnClass);", input, clazz);
            input.put("output", res);
        }
        return res;
    }

    public void executeScriptAsync(String event, String basePath, Map<String, Object> payload) {
        List<ScriptPathNameHash> activePaths = getActiveScriptsForEvent(event, basePath, true);
        Map<String, Object> input = new HashMap<>(payload);
        input.put("extensionEvent", event);
        for (ScriptPathNameHash activePath : activePaths) {
            String path = activePath.getPath();
            String name = activePath.getName();
            scriptingExecutionService.executeScriptAsync(path, name, activePath.getHash(), () -> getScript(path, name)+"\n;executeScript(extensionEvent);", input);
        }
    }

    private List<ScriptPathNameHash> getActiveScriptsForEvent(String event, String basePath, boolean async) {
        // fetch all active scripts
        // to handle override:
        // if there are active tree scripts with the same name
        // with path:
        //  - -org-event
        //  - -org
        //  - -
        // the one with the longest path win

        //generate all the paths
        // given ".0.0" it will generate
        // ".", ".0", ".0.0"

        Set<String> paths = new TreeSet<>();
        int basePathLength = basePath.length();
        for (int i = 1; i < basePathLength; i++) {
            if (basePath.charAt(i) == '-') {
                paths.add(basePath.substring(0, i));
            }
        }
        paths.add("-"); //handle first and last case
        paths.add(basePath);
        return extensionRepository.findActive(paths, async, event);
    }

    public List<ExtensionSupport> listAll() {
        return extensionRepository.listAll();
    }
}
