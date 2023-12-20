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

import alfio.manager.support.extension.ExtensionCapability;
import alfio.manager.support.extension.ExtensionEvent;
import alfio.manager.system.ExternalConfiguration;
import alfio.model.*;
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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.extension.ScriptingExecutionService.EXTENSION_CONFIGURATION_PARAMETERS;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Service
@Log4j2
@AllArgsConstructor
public class ExtensionService {

    private static final String EVALUATE_RESULT = "res = GSON.fromJson(JSON.stringify(res), returnClass);";
    private static final String PROCESS_EXTENSION_RESULT  = "var res = executeScript(extensionEvent); " + EVALUATE_RESULT;
    private static final String PROCESS_CAPABILITY_RESULT = "var res = executeCapability(capability); " + EVALUATE_RESULT;
    private static final String EXECUTE_SCRIPT = "executeScript(extensionEvent);";
    private static final String OUTPUT = "output";
    private static final String EXECUTION_KEY = "executionKey";
    private static final String EXTENSION_EVENT = "extensionEvent";
    private final ScriptingExecutionService scriptingExecutionService;
    private final ExtensionRepository extensionRepository;
    private final ExtensionLogRepository extensionLogRepository;
    private final PlatformTransactionManager platformTransactionManager;
    private final ExternalConfiguration externalConfiguration;
    private final NamedParameterJdbcTemplate jdbcTemplate;


    @AllArgsConstructor
    private static final class ExtensionLoggerImpl implements ExtensionLogger {

        private final ExtensionLogRepository extensionLogRepository;
        private final PlatformTransactionManager platformTransactionManager;
        private final String effectivePath;
        private final String path;
        private final String name;

        @Override
        public void logWarning(String msg) {
            executeInNewTransaction(s -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.WARNING));
        }

        @Override
        public void logSuccess(String msg) {
            executeInNewTransaction(s -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.SUCCESS));
        }

        @Override
        public void logError(String msg) {
            executeInNewTransaction(s -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.ERROR));
        }

        @Override
        public void logInfo(String msg) {
            executeInNewTransaction(s -> extensionLogRepository.insert(effectivePath, path, name, msg, ExtensionLog.Type.INFO));
        }

        private void executeInNewTransaction(TransactionCallback<Integer> t) {
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            TransactionTemplate template = new TransactionTemplate(platformTransactionManager, definition);
            template.execute(t);
        }
    }

    private static final class NoopExtensionLogger implements ExtensionLogger {
    }


    private ExtensionMetadata getMetadata(String name, String script) {
        return scriptingExecutionService.executeScript(
            name,
            script + "\n;GSON.fromJson(JSON.stringify(getScriptMetadata()), returnClass);", //<- ugly hack, but the interop java<->js is simpler that way...
            Collections.emptyMap(),
            ExtensionMetadata.class, new NoopExtensionLogger());
    }

    @Transactional
    public void createOrUpdate(String previousPath, String previousName, Extension script) {
        Validate.notBlank(script.getName(), "Name is mandatory");
        Validate.notBlank(script.getPath(), "Path must be defined");
        ScriptValidation validation = new ScriptValidation(script.getScript());
        validation.validate();
        String hash = DigestUtils.sha256Hex(script.getScript());
        ExtensionMetadata extensionMetadata = getMetadata(script.getName(), script.getScript());

        Validate.notBlank(extensionMetadata.getDisplayName(), "Display Name is mandatory");

        validateCapabilities(extensionMetadata);

        if(previousPath != null && previousName != null) {
            extensionRepository.deleteEventsForPath(previousPath, previousName);
        }

        if (!Objects.equals(previousPath, script.getPath()) || !Objects.equals(previousName, script.getName())) {
            extensionRepository.deleteScriptForPath(previousPath, previousName);
            extensionRepository.insert(script.getPath(), script.getName(), extensionMetadata.getDisplayName(), hash, script.isEnabled(), extensionMetadata.isAsync(), script.getScript(), extensionMetadata);
        } else {
            extensionRepository.update(script.getPath(), script.getName(), extensionMetadata.getDisplayName(), hash, script.isEnabled(), extensionMetadata.isAsync(), script.getScript(), extensionMetadata);
        }

        int extensionId = extensionRepository.getExtensionIdFor(script.getPath(), script.getName());

        for (String event : extensionMetadata.getEvents()) {
            extensionRepository.insertEvent(extensionId, event);
        }


        //
        ExtensionMetadata.Parameters parameters = extensionMetadata.getParameters();
        if (parameters != null) {
            List<ExtensionParameterKeyValue> extensionParameterKeyValue = extensionRepository.findExtensionParameterKeyValue(extensionId);
            extensionRepository.deleteExtensionParameter(extensionId);
            for (ExtensionMetadata.Field field : requireNonNullElse(parameters.getFields(), List.<ExtensionMetadata.Field>of())) {
                for (String level : parameters.getConfigurationLevels()) {
                    int confFieldId = extensionRepository.registerExtensionConfigurationMetadata(extensionId, field.getName(), field.getDescription(), field.getType(), level, field.isRequired()).getKey();
                    List<ExtensionParameterKeyValue> filteredParam = extensionParameterKeyValue.stream().filter(kv -> field.getName().equals(kv.getName()) && level.equals(kv.getConfigurationLevel())).collect(toList());
                    var parameterSources = filteredParam.stream()
                        .map(kv -> new MapSqlParameterSource("ecmId", confFieldId)
                            .addValue("confPath", kv.getConfigurationPath())
                            .addValue("value", kv.getConfigurationValue())
                        ).toArray(MapSqlParameterSource[]::new);
                    jdbcTemplate.batchUpdate(extensionRepository.bulkInsertSettingValue(), parameterSources);
                }
            }
        }
    }

    void validateCapabilities(ExtensionMetadata extensionMetadata) {
        // validate events / capabilities combination
        Set<ExtensionEvent> events = requireNonNull(extensionMetadata.getEvents(), "Events are mandatory")
            .stream()
            .map(ExtensionEvent::valueOf)
            .collect(toSet());
        Validate.isTrue(!events.isEmpty(), "Events are mandatory");
        var invalidCapabilities = requireNonNullElse(extensionMetadata.getCapabilities(), List.<String>of()).stream()
            .filter(s -> ExtensionCapability.valueOf(s).getCompatibleEvents().stream().noneMatch(events::contains))
            .collect(toSet());

        if(!invalidCapabilities.isEmpty()) {
            throw new IllegalArgumentException("Invalid capabilities: " + String.join(", ", invalidCapabilities));
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
    public void bulkUpdateEventSettings(Organization org, EventAndOrganizationId event, List<ExtensionMetadataValue> toUpdate) {
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
            .collect(toList());
        var parameterSources = filtered.stream()
            .map(kv -> new MapSqlParameterSource("ecmId", kv.getId())
                .addValue("confPath", path)
                .addValue("value", kv.getValue())
            ).toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(extensionRepository.bulkInsertSettingValue(), parameterSources);
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
        return externalConfiguration.getScript(path, name)
            .orElseGet(() -> extensionRepository.getScript(path, name));
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionSupport> getSingle(String path, String name) {
        return extensionRepository.getSingle(path, name);
    }

    @Transactional(readOnly = true)
    public Optional<ExtensionSupport> getSingle(Organization organization, EventAndOrganizationId event, String name) {
        Set<String> paths = generatePossiblePath(toPath(new EventAndOrganizationId(organization.getId(), event.getId())), Comparator.reverseOrder());
        return extensionRepository.getSingle(paths, name);
    }

    @Transactional(readOnly = true)
    public boolean isCapabilitySupported(ExtensionCapability capability, PurchaseContext purchaseContext) {
        // load compatible scripts
        var externalScripts = externalConfiguration.getAllExtensionsForCapability(capability);
        if(!externalScripts.isEmpty()) {
            return true;
        }
        var paths = generatePossiblePath(toPath(purchaseContext), Comparator.reverseOrder());
        int count = extensionRepository.countScriptsSupportingCapability(paths, List.of(capability.name()));
        return count > 0;
    }

    @Transactional(readOnly = true)
    public Set<ExtensionCapabilitySummary> getSupportedCapabilities(Set<ExtensionCapability> requested, PurchaseContext purchaseContext) {
        var externalScriptsSupportedCapabilities = externalConfiguration.getSupportedCapabilities(requested);
        if (requested.stream().allMatch(capability -> externalScriptsSupportedCapabilities.stream().anyMatch(sc -> sc.getCapability() == capability))) {
            return externalScriptsSupportedCapabilities;
        }
        var result = new HashSet<>(externalScriptsSupportedCapabilities);
        var paths = generatePossiblePath(toPath(purchaseContext), Comparator.reverseOrder());
        result.addAll(extensionRepository.getSupportedCapabilities(paths, ExtensionCapability.toString(requested)));
        return result;
    }

    private Optional<ScriptPathNameHash> getFirstScriptSupportingCapability(ExtensionCapability capability, String basePath) {
        var externalConfCapabilities = externalConfiguration.getAllExtensionsForCapability(capability);
        if(!externalConfCapabilities.isEmpty()) {
            return Optional.of(externalConfCapabilities.get(0));
        }
        return extensionRepository.getFirstScriptForCapability(generatePossiblePath(basePath), capability.name());
    }

    public <T> Optional<T> executeCapability(ExtensionCapability capability,
                                             String basePath,
                                             Map<String, Object> params,
                                             Class<T> resultType) {
        return getFirstScriptSupportingCapability(capability, basePath)
            .map(scriptPathNameHash -> {
                Map<String, Object> context = new HashMap<>(params);
                context.put("capability", capability.name());
                context.put(OUTPUT, null);
                context.put(EXECUTION_KEY, UUID.randomUUID().toString());
                context = internalExecuteScript(scriptPathNameHash, context, basePath, false, PROCESS_CAPABILITY_RESULT, resultType);
                return resultType.cast(context.get(OUTPUT));
            });
    }

    public <T> T executeScriptsForEvent(String event, String basePath, Map<String, Object> payload, Class<T> clazz) {
        List<ScriptPathNameHash> activePaths = getActiveScriptsForEvent(event, basePath, false);
        Map<String, Object> context = new HashMap<>(payload);
        context.put(EXTENSION_EVENT, event);
        context.put(EXECUTION_KEY, UUID.randomUUID().toString());
        context.put(OUTPUT, null);
        for (ScriptPathNameHash activePath : activePaths) {
            context = internalExecuteScript(activePath, context, basePath, false, PROCESS_EXTENSION_RESULT, clazz);
        }
        return clazz.cast(context.get(OUTPUT));
    }

    public void executeScriptAsync(String event, String basePath, Map<String, Object> payload) {
        List<ScriptPathNameHash> activePaths = getActiveScriptsForEvent(event, basePath, true);
        Map<String, Object> input = new HashMap<>(payload);
        input.put(EXTENSION_EVENT, event);
        input.put(EXECUTION_KEY, UUID.randomUUID().toString());
        for (ScriptPathNameHash activePath : activePaths) {
            input = internalExecuteScript(activePath, input, basePath, true, EXECUTE_SCRIPT, Void.class);
        }
    }

    public void retryFailedAsyncScript(String path, String name, Map<String, Object> payload) {
        var extensionEvent = (String) payload.get(EXTENSION_EVENT);
        var activePathOptional = getActiveScriptsForEvent(extensionEvent, path, true).stream()
            .filter(nh -> nh.getName().equals(name))
            .findFirst();
        activePathOptional.ifPresent(scriptPathNameHash ->
            internalExecuteScript(scriptPathNameHash, payload, path, false, EXECUTE_SCRIPT + "\nvar res = null;", Void.class, true));

    }

    private <T> Map<String, Object> internalExecuteScript(ScriptPathNameHash activePath,
                                                          Map<String, Object> input,
                                                          String basePath,
                                                          boolean async,
                                                          String executeInstruction,
                                                          Class<T> expectedResult) {
        return internalExecuteScript(activePath, input, basePath, async, executeInstruction, expectedResult, false);
    }

    private <T> Map<String, Object> internalExecuteScript(ScriptPathNameHash activePath,
                                           Map<String, Object> input,
                                           String basePath,
                                           boolean async,
                                           String executeInstruction,
                                           Class<T> expectedResult,
                                           boolean throwErrorIfNotExecuted) {
        String path = activePath.getPath();
        String name = activePath.getName();
        Pair<Set<String>, Map<String, Object>> params = addExtensionParameters(input, basePath, activePath);
        var context = params.getRight();
        ExtensionLogger extLogger = new ExtensionLoggerImpl(extensionLogRepository, platformTransactionManager, basePath, path, name);

        if(params.getLeft().isEmpty()) {
            Supplier<String> scriptGetter = () -> getScript(path, name)+"\n;"+executeInstruction;
            if(async) {
                scriptingExecutionService.executeScriptAsync(path, name, activePath.getHash(), scriptGetter, context, extLogger);
            } else {
                Object res = scriptingExecutionService.executeScript(name, activePath.getHash(), scriptGetter, context, expectedResult, extLogger);
                context.put(OUTPUT, res);
            }
        } else {
            extLogger.logWarning("script not run, missing parameters: " + params.getLeft());
            if (throwErrorIfNotExecuted) {
                throw new IllegalStateException("Script not run, missing parameters: "+ params.getLeft());
            }
        }
        return context;
    }

    /*
    * Return a copy of the input with added parameters and a set of missing mandatory parameters, if any
    * */
    private Pair<Set<String>, Map<String,Object>> addExtensionParameters(Map<String, Object> input, String basePath, ScriptPathNameHash activePath) {
        if(ExternalConfiguration.isExternalPath(activePath.getPath())) {
            return getExternalExtensionParameters(input, activePath);
        } else {
            return getExtensionParameters(input, basePath, activePath);
        }
    }

    private Pair<Set<String>, Map<String, Object>> getExternalExtensionParameters(Map<String, Object> input, ScriptPathNameHash activePath) {
        Map<String, Object> copy = new HashMap<>(input);
        // we assume that external parameters are defined correctly.
        copy.put(EXTENSION_CONFIGURATION_PARAMETERS, externalConfiguration.getParametersForExtension(activePath.getName()));
        return Pair.of(Set.of(), copy);
    }


    private Pair<Set<String>, Map<String, Object>> getExtensionParameters(Map<String, Object> input, String basePath, ScriptPathNameHash activePath) {
        Map<String, Object> copy = new HashMap<>(input);
        Map<String, String> nameAndValues = extensionRepository.findParametersForScript(activePath.getName(), activePath.getPath(), generatePossiblePath(basePath))
            .stream()
            .collect(Collectors.toMap(NameAndValue::getName, NameAndValue::getValue));

        Set<String> mandatory = new HashSet<>(extensionRepository.findMandatoryParametersForScript(activePath.getName(), activePath.getPath()));

        mandatory.removeAll(nameAndValues.keySet());

        copy.put(EXTENSION_CONFIGURATION_PARAMETERS, nameAndValues);
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
        var allExtensions = new ArrayList<>(externalConfiguration.getAllExtensionsFor(event, async));
        allExtensions.addAll(extensionRepository.findActive(paths, async, event));
        return allExtensions;
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

    public static String toPath(EventAndOrganizationId event) {
        return "-" + event.getOrganizationId() + "-" + event.getId();
    }

    public static String toPath(PurchaseContext purchaseContext) {
        if(purchaseContext == null) {
            return "-";
        }
        int organizationId = purchaseContext.getOrganizationId();
        return purchaseContext.event().map(e -> toPath((EventAndOrganizationId) e)).orElseGet(() -> "-" + organizationId);
    }
}
