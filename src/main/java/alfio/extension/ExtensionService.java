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

import alfio.manager.ExtensionManager;
import alfio.model.Event;
import alfio.model.ExtensionLog;
import alfio.model.ExtensionSupport;
import alfio.model.ExtensionSupport.*;
import alfio.model.user.Organization;
import alfio.repository.ExtensionLogRepository;
import alfio.repository.ExtensionRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Log4j2
@AllArgsConstructor
public class ExtensionService {

    private final ScriptingExecutionService scriptingExecutionService;

    private final ExtensionRepository extensionRepository;

    private final ExtensionLogRepository extensionLogRepository;

    private final PlatformTransactionManager platformTransactionManager;

    private final static String PRELOAD_SCRIPT = "\nvar HashMap = Java.type('java.util.HashMap');\n" +
        "var ExtensionUtils = Java.type('alfio.extension.ExtensionUtils');\n";


    @AllArgsConstructor
    private static final class ExtensionLoggerImpl implements ExtensionLogger {

        private final ExtensionLogRepository extensionLogRepository;
        private final PlatformTransactionManager platformTransactionManager;
        private final String effectivePath;
        private final String path;
        private final String name;

        @Override
        public void logWarning(String msg) {
            executeInNewTransaction((s) -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.WARNING));
        }

        @Override
        public void logSuccess(String msg) {
            executeInNewTransaction((s) -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.SUCCESS));
        }

        @Override
        public void logError(String msg) {
            executeInNewTransaction((s) -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.ERROR));
        }

        @Override
        public void logInfo(String msg) {
            executeInNewTransaction((s) -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.INFO));
        }

        private void executeInNewTransaction(TransactionCallback<Integer> t) {
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate template = new TransactionTemplate(platformTransactionManager, definition);
            template.execute(t);
        }
    }

    private static final class NoopExtensionLogger implements ExtensionLogger {
    }


    private static ExtensionMetadata getMetadata(String name, String script) {
        return ScriptingExecutionService.executeScript(
            name,
            PRELOAD_SCRIPT + script + "\n;GSON.fromJson(JSON.stringify(getScriptMetadata()), returnClass);", //<- ugly hack, but the interop java<->js is simpler that way...
            Collections.emptyMap(),
            ExtensionMetadata.class, new NoopExtensionLogger());
    }

    @Transactional
    public void createOrUpdate(String previousPath, String previousName, Extension script) {
        Validate.notBlank(script.getName(), "Name is mandatory");
        Validate.notBlank(script.getPath(), "Path must be defined");
        String hash = DigestUtils.sha256Hex(script.getScript());
        ExtensionMetadata extensionMetadata = getMetadata(script.getName(), script.getScript());

        Validate.notBlank(extensionMetadata.displayName, "Display Name is mandatory");

        if(previousPath != null && previousName != null) {
            extensionRepository.deleteEventsForPath(previousPath, previousName);
        }

        if (!Objects.equals(previousPath, script.getPath()) || !Objects.equals(previousName, script.getName())) {
            extensionRepository.deleteScriptForPath(previousPath, previousName);
            extensionRepository.insert(script.getPath(), script.getName(), extensionMetadata.displayName, hash, script.isEnabled(), extensionMetadata.async, script.getScript());
        } else {
            extensionRepository.update(script.getPath(), script.getName(), extensionMetadata.displayName, hash, script.isEnabled(), extensionMetadata.async, script.getScript());
        }

        int extensionId = extensionRepository.getExtensionIdFor(script.getPath(), script.getName());

        for (String event : extensionMetadata.events) {
            extensionRepository.insertEvent(extensionId, event);
        }


        //
        ExtensionMetadata.Parameters parameters = extensionMetadata.getParameters();
        if (parameters != null) {
            List<ExtensionParameterKeyValue> extensionParameterKeyValue = extensionRepository.findExtensionParameterKeyValue(extensionId);
            extensionRepository.deleteExtensionParameter(extensionId);
            for (ExtensionMetadata.Field field : parameters.getFields()) {
                for (String level : parameters.getConfigurationLevels()) {
                    int confFieldId = extensionRepository.registerExtensionConfigurationMetadata(extensionId, field.getName(), field.getDescription(), field.getType(), level, field.isRequired()).getKey();
                    List<ExtensionParameterKeyValue> filteredParam = extensionParameterKeyValue.stream().filter(kv -> field.getName().equals(kv.getName()) && level.equals(kv.getConfigurationLevel())).collect(Collectors.toList());
                    for(ExtensionParameterKeyValue kv : filteredParam) {
                        //TODO: can be optimized with a bulk insert...
                        extensionRepository.insertSettingValue(confFieldId, kv.getConfigurationPath(), kv.getConfigurationValue());
                    }
                }
            }
        }
    }

    public List<ExtensionParameterMetadataAndValue> getConfigurationParametersFor(String basePath, String pathPattern, String configurationLevel) {
        return extensionRepository.getParametersForLevelAndPath(configurationLevel, generatePossiblePath(basePath), pathPattern);
    }

    @Transactional
    public void bulkUpdateSystemSettings(List<ExtensionMetadataValue> toUpdate) {
        deleteAndInsertSetting("SYSTEM", "-", toUpdate);
    }

    @Transactional
    public void bulkUpdateOrganizationSettings(Organization org, List<ExtensionMetadataValue> toUpdate) {
        String path = "-" + org.getId();
        deleteAndInsertSetting("ORGANIZATION", path, toUpdate);
    }

    @Transactional
    public void bulkUpdateEventSettings(Organization org, Event event, List<ExtensionMetadataValue> toUpdate) {
        String path = "-" + org.getId() + "-" + event.getId();
        deleteAndInsertSetting("EVENT", path, toUpdate);
    }

    @Transactional
    public void deleteSettingValue(int id, String path) {
        extensionRepository.deleteSettingValue(id, path);
    }

    private void deleteAndInsertSetting(String level, String path, List<ExtensionMetadataValue> toUpdate) {
        extensionRepository.deleteSettingValue(level, path);
        List<ExtensionMetadataValue> toUpdate2 = (toUpdate == null ? Collections.emptyList() : toUpdate);
        List<ExtensionMetadataValue> filtered = toUpdate2.stream()
            .filter(f -> StringUtils.trimToNull(f.getValue()) != null)
            .collect(Collectors.toList());
        for (ExtensionMetadataValue v : filtered) {
            extensionRepository.insertSettingValue(v.getId(), path, v.getValue());
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

    @Transactional(readOnly = true)
    public String getScript(String path, String name) {
        return extensionRepository.getScript(path, name);
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionSupport> getSingle(String path, String name) {
        return extensionRepository.getSingle(path, name);
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionSupport> getSingle(Organization organization, Event event, String name) {
        Set<String> paths = generatePossiblePath(ExtensionManager.toPath(organization.getId(), event.getId()), Comparator.reverseOrder());
        return extensionRepository.getSingle(paths, name);
    }

    public <T> T executeScriptsForEvent(String event, String basePath, Map<String, Object> payload, Class<T> clazz) {
        List<ScriptPathNameHash> activePaths = getActiveScriptsForEvent(event, basePath, false);
        T res = null;
        Map<String, Object> input = new HashMap<>(payload);
        input.put("extensionEvent", event);
        input.put("output", null);
        for (ScriptPathNameHash activePath : activePaths) {
            String path = activePath.getPath();
            String name = activePath.getName();
            Pair<Set<String>, Map<String, Object>> params = addExtensionParameters(input, basePath, activePath);
            input = params.getRight();
            ExtensionLogger extLogger = new ExtensionLoggerImpl(extensionLogRepository, platformTransactionManager, basePath, path, name);

            if(params.getLeft().isEmpty()) {
                res = scriptingExecutionService.executeScript(name, activePath.getHash(),
                    () -> PRELOAD_SCRIPT + getScript(path, name)+"\n;GSON.fromJson(JSON.stringify(executeScript(extensionEvent)), returnClass);", input, clazz, extLogger);
                input.put("output", res);
            } else {
                extLogger.logInfo("script not run, missing parameters: " + params.getLeft());
            }
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
            Pair<Set<String>, Map<String, Object>> params = addExtensionParameters(input, basePath, activePath);
            input = params.getRight();
            ExtensionLogger extLogger = new ExtensionLoggerImpl(extensionLogRepository, platformTransactionManager, basePath, path, name);

            if(params.getLeft().isEmpty()) {
                scriptingExecutionService.executeScriptAsync(path, name, activePath.getHash(), () -> PRELOAD_SCRIPT + getScript(path, name)+"\n;executeScript(extensionEvent);", input, extLogger);
            } else {
                extLogger.logInfo("script not run, missing parameters: " + params.getLeft());
            }
        }
    }

    /*
    * Return a copy of the input with added parameters and a set of missing mandatory parameters, if any
    * */
    private Pair<Set<String>, Map<String,Object>> addExtensionParameters(Map<String, Object> input, String basePath, ScriptPathNameHash activePath) {
        Map<String, Object> copy = new HashMap<>(input);
        Map<String, String> nameAndValues = extensionRepository.findParametersForScript(activePath.getName(), activePath.getPath(), generatePossiblePath(basePath))
            .stream()
            .collect(Collectors.toMap(NameAndValue::getName, NameAndValue::getValue));

        Set<String> mandatory = new HashSet<>(extensionRepository.findMandatoryParametersForScript(activePath.getName(), activePath.getPath()));

        mandatory.removeAll(nameAndValues.keySet());

        copy.put("extensionParameters", nameAndValues);
        return Pair.of(mandatory, copy);
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
        Set<String> paths = generatePossiblePath(basePath);
        return extensionRepository.findActive(paths, async, event);
    }

    private static Set<String> generatePossiblePath(String basePath, Comparator<String> comparator) {
        //generate all the paths
        // given "-0-0" it will generate
        // "-", "-0", "-0-0"
        Set<String> paths = new TreeSet<>(comparator);
        int basePathLength = basePath.length();
        for (int i = 1; i < basePathLength; i++) {
            if (basePath.charAt(i) == '-') {
                paths.add(basePath.substring(0, i));
            }
        }
        paths.add("-"); //handle first and last case
        paths.add(basePath);
        return paths;
    }

    private static Set<String> generatePossiblePath(String basePath) {
        return generatePossiblePath(basePath, Comparator.naturalOrder());
    }

    @Transactional(readOnly = true)
    public List<ExtensionSupport> listAll() {
        return extensionRepository.listAll();
    }


    @Transactional(readOnly = true)
    public Pair<List<ExtensionLog>, Integer> getLog(String path, String name, ExtensionLog.Type type, int pageSize, int offset) {
        String typeAsString = type != null ? type.name() : null;
        int count = extensionLogRepository.countPages(path, name, typeAsString);
        List<ExtensionLog> logs = extensionLogRepository.getPage(path, name, typeAsString, pageSize, offset);
        return Pair.of(logs, count);
    }
}
