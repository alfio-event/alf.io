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
package alfio.manager.system;

import alfio.manager.system.ConfigurationLevels.CategoryLevel;
import alfio.manager.system.ConfigurationLevels.EventLevel;
import alfio.manager.system.ConfigurationLevels.OrganizationLevel;
import alfio.manager.user.UserManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.TicketReservation;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.*;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.model.system.ConfigurationPathLevel.*;

@Component
@Transactional
@Log4j2
@AllArgsConstructor
public class ConfigurationManager {

    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> ORGANIZATION_CONFIGURATION = collectConfigurationKeysByCategory(ORGANIZATION);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> EVENT_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.EVENT);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> CATEGORY_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.TICKET_CATEGORY);

    private static final Predicate<ConfigurationModification> TO_BE_SAVED = c -> Optional.ofNullable(c.getId()).orElse(-1) > -1 || !StringUtils.isBlank(c.getValue());

    private final ConfigurationRepository configurationRepository;
    private final UserManager userManager;
    private final EventRepository eventRepository;

    //TODO: refactor, not the most beautiful code, find a better solution...
    private Optional<Configuration> findByConfigurationPathAndKey(ConfigurationPath path, ConfigurationKeys key) {
        switch (path.pathLevel()) {
            case SYSTEM: return configurationRepository.findOptionalByKey(key.getValue());
            case ORGANIZATION: {
                OrganizationConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByOrganizationAndKey(o.getId(), key.getValue()));
            }
            case EVENT: {
                EventConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByEventAndKey(o.getOrganizationId(),
                    o.getId(), key.getValue()));
            }
            case TICKET_CATEGORY: {
                TicketCategoryConfigurationPath o = from(path);
                return selectPath(configurationRepository.findByTicketCategoryAndKey(o.getOrganizationId(),
                    o.getEventId(), o.getId(), key.getValue()));
            }
        }
        throw new IllegalStateException("Can't reach here");
    }

    /**
     * Select the most "precise" configuration in the given list.
     *
     * @param conf
     * @return
     */
    private Optional<Configuration> selectPath(List<Configuration> conf) {
        return conf.size() == 1 ? Optional.of(conf.get(0)) : conf.stream().max(Comparator.comparing(Configuration::getConfigurationPathLevel));
    }

    //meh
    @SuppressWarnings("unchecked")
    private static <T> T from(ConfigurationPath c) {
        return (T) c;
    }

    // begin SYSTEM related configuration methods

    public void saveConfig(ConfigurationPathKey pathKey, String value) {
        ConfigurationPath path = pathKey.getPath();
        switch (path.pathLevel()) {
            case SYSTEM:
                saveSystemConfiguration(pathKey.getKey(), value);
                break;
            case ORGANIZATION:
                OrganizationConfigurationPath orgPath = (OrganizationConfigurationPath) path;
                saveOrganizationConfiguration(orgPath.getId(), pathKey.getKey().name(), value);
                break;
            case EVENT:
                EventConfigurationPath eventPath = (EventConfigurationPath) path;
                saveEventConfiguration(eventPath.getId(), eventPath.getOrganizationId(), pathKey.getKey().name(), value);
                break;
        }
    }

    public void saveAllSystemConfiguration(List<ConfigurationModification> list) {
        list.forEach(c -> saveSystemConfiguration(ConfigurationKeys.fromString(c.getKey()), c.getValue()));
    }

    private void saveOrganizationConfiguration(int organizationId, String key, String optionValue) {
        Optional<String> value = evaluateValue(key, optionValue);
        Optional<Configuration> existing = configurationRepository.findByKeyAtOrganizationLevel(organizationId, key);
        if (value.isEmpty()) {
            configurationRepository.deleteOrganizationLevelByKey(key, organizationId);
        } else if (existing.isPresent()) {
            configurationRepository.updateOrganizationLevel(organizationId, key, value.get());
        } else {
            configurationRepository.insertOrganizationLevel(organizationId, key, value.get(), ConfigurationKeys.fromString(key).getDescription());
        }
    }

    public void saveAllOrganizationConfiguration(int organizationId, List<ConfigurationModification> list, String username) {
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), organizationId), "Cannot update settings, user is not owner");
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> saveOrganizationConfiguration(organizationId, c.getKey(), c.getValue()));
    }

    private void saveEventConfiguration(int eventId, int organizationId, String key, String optionValue) {
        Optional<Configuration> existing = configurationRepository.findByKeyAtEventLevel(eventId, organizationId, key);
        Optional<String> value = evaluateValue(key, optionValue);
        if(value.isEmpty()) {
            configurationRepository.deleteEventLevelByKey(key, eventId);
        } else if (existing.isPresent()) {
            configurationRepository.updateEventLevel(eventId, organizationId, key, value.get());
        } else {
            configurationRepository.insertEventLevel(organizationId, eventId, key, value.get(), ConfigurationKeys.fromString(key).getDescription());
        }
    }

    public void saveAllEventConfiguration(int eventId, int organizationId, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        Validate.isTrue(userManager.isOwnerOfOrganization(user, organizationId), "Cannot update settings, user is not owner");
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        Validate.notNull(event, "event does not exist");
        if(organizationId != event.getOrganizationId()) {
            Validate.isTrue(userManager.isOwnerOfOrganization(user, event.getOrganizationId()), "Cannot update settings, user is not owner of event");
        }
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> saveEventConfiguration(eventId, organizationId, c.getKey(), c.getValue()));
    }

    public void saveCategoryConfiguration(int categoryId, int eventId, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        Validate.notNull(event, "event does not exist");
        Validate.isTrue(userManager.isOwnerOfOrganization(user, event.getOrganizationId()), "Cannot update settings, user is not owner of event");
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> {
                Optional<Configuration> existing = configurationRepository.findByKeyAtCategoryLevel(eventId, event.getOrganizationId(), categoryId, c.getKey());
                Optional<String> value = evaluateValue(c.getKey(), c.getValue());
                if(value.isEmpty()) {
                    configurationRepository.deleteCategoryLevelByKey(c.getKey(), eventId, categoryId);
                } else if (existing.isPresent()) {
                    configurationRepository.updateCategoryLevel(eventId, event.getOrganizationId(), categoryId, c.getKey(), value.get());
                } else {
                    configurationRepository.insertTicketCategoryLevel(event.getOrganizationId(), eventId, categoryId, c.getKey(), value.get(), ConfigurationKeys.fromString(c.getKey()).getDescription());
                }
            });
    }

    private Optional<String> evaluateValue(String key, String value) {
        if(ConfigurationKeys.fromString(key).isBooleanComponentType()) {
            return Optional.ofNullable(StringUtils.trimToNull(value));
        }
        return Optional.of(Objects.requireNonNull(value));
    }

    private Optional<Boolean> getThreeStateValue(String value) {
        return Optional.ofNullable(StringUtils.trimToNull(value)).map(Boolean::parseBoolean);
    }

    public void saveSystemConfiguration(ConfigurationKeys key, String value) {
        Optional<Configuration> conf = findByConfigurationPathAndKey(Configuration.system(), key);
        if(key.isBooleanComponentType()) {
            Optional<Boolean> state = getThreeStateValue(value);
            if(conf.isPresent()) {
                if(state.isPresent()) {
                    configurationRepository.update(key.getValue(), value);
                } else {
                    configurationRepository.deleteByKey(key.getValue());
                }
            } else {
                state.ifPresent(v -> configurationRepository.insert(key.getValue(), v.toString(), key.getDescription()));
            }
        } else {
            Optional<String> valueOpt = Optional.ofNullable(value);
            if(conf.isEmpty()) {
                valueOpt.ifPresent(v -> configurationRepository.insert(key.getValue(), v, key.getDescription()));
            } else {
                configurationRepository.update(key.getValue(), value);
            }
        }
    }

    /**
     * Checks if the basic options have been already configured:
     * <ul>
     *     <li>Google maps' api keys</li>
     *     <li>Base application URL</li>
     *     <li>E-Mail</li>
     * </ul>
     * @return {@code true} if there are missing options, {@code true} otherwise
     */
    public boolean isBasicConfigurationNeeded() {
        return ConfigurationKeys.basic().stream()
            .anyMatch(key -> {
                boolean absent = configurationRepository.findOptionalByKey(key.getValue()).isEmpty();
                if (absent) {
                    log.warn("cannot find a value for " + key.getValue());
                }
                return absent;
            });
    }

    private Predicate<Configuration> checkActualConfigurationLevel(boolean isAdmin, ConfigurationPathLevel level) {
        return conf -> isAdmin || conf.getConfigurationKey().supports(level);
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadOrganizationConfig(int organizationId, String username) {
        User user = userManager.findUserByUsername(username);
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return Collections.emptyMap();
        }
        boolean isAdmin = userManager.isAdmin(user);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findOrganizationConfiguration(organizationId).stream().filter(checkActualConfigurationLevel(isAdmin, ORGANIZATION)).sorted().collect(groupByCategory());
        String paymentMethodsBlacklist = getFor(ConfigurationKeys.PAYMENT_METHODS_BLACKLIST, new OrganizationLevel(organizationId)).getValueOrDefault("");
        Map<SettingCategory, List<Configuration>> result = groupByCategory(isAdmin ? union(SYSTEM, ORGANIZATION) : ORGANIZATION_CONFIGURATION, existing);
        List<SettingCategory> toBeRemoved = PaymentProxy.availableProxies()
            .stream()
            .filter(pp -> paymentMethodsBlacklist.contains(pp.getKey()))
            .flatMap(pp -> pp.getSettingCategories().stream())
            .collect(Collectors.toList());

        if(toBeRemoved.isEmpty()) {
            return result;
        } else {
            return result.entrySet().stream()
                .filter(entry -> !toBeRemoved.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    public String getSingleConfigForEvent(int eventId, String keyAsString, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        int organizationId = event.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return null;
        }
        var key = safeValueOf(keyAsString);
        return configurationRepository.findByEventAndKey(eventId, organizationId, key.name())
            .stream()
            .findFirst()
            .map(Configuration::getValue)
            .orElse(null);
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadEventConfig(int eventId, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        int organizationId = event.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return Collections.emptyMap();
        }
        boolean isAdmin = userManager.isAdmin(user);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findEventConfiguration(organizationId, eventId).stream().filter(checkActualConfigurationLevel(isAdmin, EVENT)).sorted().collect(groupByCategory());
        boolean offlineCheckInEnabled = areBooleanSettingsEnabledForEvent(true, ALFIO_PI_INTEGRATION_ENABLED, OFFLINE_CHECKIN_ENABLED).test(event);
        return removeAlfioPISettingsIfNeeded(offlineCheckInEnabled, groupByCategory(isAdmin ? union(SYSTEM, EVENT) : EVENT_CONFIGURATION, existing));
    }

    public Predicate<EventAndOrganizationId> areBooleanSettingsEnabledForEvent(boolean defaultValue, ConfigurationKeys... keys) {
        return event -> getFor(Set.of(keys), ConfigurationLevel.event(event)).entrySet().stream().allMatch(kv -> kv.getValue().getValueAsBooleanOrDefault(defaultValue));
    }

    private static Map<ConfigurationKeys.SettingCategory, List<Configuration>> removeAlfioPISettingsIfNeeded(boolean offlineCheckInEnabled, Map<ConfigurationKeys.SettingCategory, List<Configuration>> settings) {
        if(offlineCheckInEnabled) {
            return settings;
        }
        return settings.entrySet().stream()
            .filter(e -> e.getKey() != ConfigurationKeys.SettingCategory.ALFIO_PI)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static Map<ConfigurationKeys.SettingCategory, List<Configuration>> union(ConfigurationPathLevel... levels) {
        List<Configuration> configurations = Arrays.stream(levels)
            .sorted(ConfigurationPathLevel.COMPARATOR.reversed())
            .flatMap(l -> ConfigurationKeys.byPathLevel(l).stream().map(mapEmptyKeys(l)))
            .sorted((c1, c2) -> new CompareToBuilder().append(c2.getConfigurationPathLevel(), c1.getConfigurationPathLevel()).append(c1.getConfigurationKey(), c2.getConfigurationKey()).toComparison())
            .collect(LinkedList::new, (List<Configuration> list, Configuration conf) -> {
                int existing = (int) list.stream().filter(c -> c.getConfigurationKey() == conf.getConfigurationKey()).count();
                if (existing == 0) {
                    list.add(conf);
                }
            }, (l1, l2) -> {
            });
        return configurations.stream().collect(groupByCategory());
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadCategoryConfig(int eventId, int categoryId, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        int organizationId = event.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return Collections.emptyMap();
        }
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findCategoryConfiguration(organizationId, eventId, categoryId).stream().sorted().collect(groupByCategory());
        return groupByCategory(CATEGORY_CONFIGURATION, existing);
    }

    private Map<ConfigurationKeys.SettingCategory, List<Configuration>> groupByCategory(Map<ConfigurationKeys.SettingCategory, List<Configuration>> all, Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing) {
        return all.entrySet().stream()
            .map(e -> {
                ConfigurationKeys.SettingCategory key = e.getKey();
                Set<Configuration> entries = new TreeSet<>(e.getValue());
                if(existing.containsKey(key)) {
                    List<Configuration> configurations = existing.get(key);
                    entries.removeAll(configurations);
                    entries.addAll(configurations);
                }
                return Pair.of(key, new ArrayList<>(entries));
            })
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadAllSystemConfigurationIncludingMissing(String username) {
        if(!userManager.isAdmin(userManager.findUserByUsername(username))) {
            return Collections.emptyMap();
        }
        final List<Configuration> existing = configurationRepository.findSystemConfiguration()
                .stream()
                .filter(c -> !ConfigurationKeys.fromString(c.getKey()).isInternal())
                .collect(Collectors.toList());
        final List<Configuration> missing = Arrays.stream(ConfigurationKeys.visible())
                .filter(k -> existing.stream().noneMatch(c -> c.getKey().equals(k.getValue())))
                .map(mapEmptyKeys(ConfigurationPathLevel.SYSTEM))
                .collect(Collectors.toList());
        List<Configuration> result = new LinkedList<>(existing);
        result.addAll(missing);
        return result.stream().sorted().collect(groupByCategory());
    }

    private static Collector<Configuration, ?, Map<ConfigurationKeys.SettingCategory, List<Configuration>>> groupByCategory() {
        return Collectors.groupingBy(c -> c.getConfigurationKey().getCategory());
    }

    private static Function<ConfigurationKeys, Configuration> mapEmptyKeys(ConfigurationPathLevel level) {
        return k -> new Configuration(-1, k.getValue(), null, level);
    }

    public void deleteKey(String key) {
        configurationRepository.deleteByKey(key);
    }

    public void deleteOrganizationLevelByKey(String key, int organizationId, String username) {
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), organizationId), "User is not owner of the organization. Therefore, delete is not allowed.");
        configurationRepository.deleteOrganizationLevelByKey(key, organizationId);
    }

    public void deleteEventLevelByKey(String key, int eventId, String username) {
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        Validate.notNull(event, "Wrong event id");
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), event.getOrganizationId()), "User is not owner of the organization. Therefore, delete is not allowed.");
        configurationRepository.deleteEventLevelByKey(key, eventId);
    }

    public void deleteCategoryLevelByKey(String key, int eventId, int categoryId, String username) {
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        Validate.notNull(event, "Wrong event id");
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), event.getOrganizationId()), "User is not owner of the organization. Therefore, delete is not allowed.");
        configurationRepository.deleteCategoryLevelByKey(key, eventId, categoryId);
    }

    private static Map<ConfigurationKeys.SettingCategory, List<Configuration>> collectConfigurationKeysByCategory(ConfigurationPathLevel pathLevel) {
        return ConfigurationKeys.byPathLevel(pathLevel)
            .stream()
            .map(mapEmptyKeys(pathLevel))
            .sorted()
            .collect(groupByCategory());
    }

    public String getShortReservationID(EventAndOrganizationId event, TicketReservation reservation) {
        var conf = getFor(Set.of(USE_INVOICE_NUMBER_AS_ID, PARTIAL_RESERVATION_ID_LENGTH), ConfigurationLevel.event(event));
        if(conf.get(USE_INVOICE_NUMBER_AS_ID).getValueAsBooleanOrDefault(false) && reservation.getHasInvoiceNumber()) {
            return reservation.getInvoiceNumber();
        }
        return StringUtils.substring(reservation.getId(), 0, conf.get(PARTIAL_RESERVATION_ID_LENGTH).getValueAsIntOrDefault(8)).toUpperCase();
    }

    public String getPublicReservationID(EventAndOrganizationId event, TicketReservation reservation) {
        if(getFor(USE_INVOICE_NUMBER_AS_ID, ConfigurationLevel.event(event)).getValueAsBooleanOrDefault(false) && reservation.getHasInvoiceNumber()) {
            return reservation.getInvoiceNumber();
        }
        return reservation.getId();
    }

    public boolean hasAllConfigurationsForInvoice(EventAndOrganizationId event) {
        var r = getFor(Set.of(INVOICE_ADDRESS, VAT_NR), ConfigurationLevel.event(event));
        return hasAllConfigurationsForInvoice(r);
    }

    /**
     * @param configurationValues note: require keys INVOICE_ADDRESS, VAT_NR
     * @return
     */
    public boolean hasAllConfigurationsForInvoice(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(INVOICE_ADDRESS) && configurationValues.containsKey(VAT_NR));
        return configurationValues.get(INVOICE_ADDRESS).isPresent() && configurationValues.get(VAT_NR).isPresent();
    }

    public boolean isRecaptchaForOfflinePaymentAndFreeEnabled(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS) && configurationValues.containsKey(RECAPTCHA_API_KEY));
        return configurationValues.get(ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS).getValueAsBooleanOrDefault(false) &&
            configurationValues.get(RECAPTCHA_API_KEY).getValueOrDefault(null) != null;
    }

    public boolean isRecaptchaForOfflinePaymentAndFreeEnabled(ConfigurationLevel configurationLevel) {
        var conf = getFor(Set.of(ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS, RECAPTCHA_API_KEY), configurationLevel);
        return isRecaptchaForOfflinePaymentAndFreeEnabled(conf);
    }

    // https://github.com/alfio-event/alf.io/issues/573
    public boolean canGenerateReceiptOrInvoiceToCustomer(EventAndOrganizationId event) {
        return !isItalianEInvoicingEnabled(event);
    }

    public boolean canGenerateReceiptOrInvoiceToCustomer(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        return !isItalianEInvoicingEnabled(configurationValues);
    }

    public boolean isInvoiceOnly(EventAndOrganizationId event) {
        var res = getFor(Set.of(GENERATE_ONLY_INVOICE, ENABLE_ITALY_E_INVOICING), ConfigurationLevel.event(event));
        return isInvoiceOnly(res);
    }

    /**
     * @param configurationValues note: require the key GENERATE_ONLY_INVOICE and ENABLE_ITALY_E_INVOICING to be present
     * @return
     */
    public boolean isInvoiceOnly(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(GENERATE_ONLY_INVOICE) && configurationValues.containsKey(ENABLE_ITALY_E_INVOICING));
        return configurationValues.get(GENERATE_ONLY_INVOICE).getValueAsBooleanOrDefault(false) || configurationValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault(false);
    }

    public boolean isItalianEInvoicingEnabled(EventAndOrganizationId event) {
        var res = getFor(List.of(ENABLE_ITALY_E_INVOICING), ConfigurationLevel.event(event));
        return isItalianEInvoicingEnabled(res);
    }

    public boolean isItalianEInvoicingEnabled(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_ITALY_E_INVOICING));
        return configurationValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault(false);
    }

    //

    public MaybeConfiguration getForSystem(ConfigurationKeys key) {
        return getFor(Set.of(key), ConfigurationLevel.system()).get(key);
    }

    public MaybeConfiguration getFor(ConfigurationKeys key, ConfigurationLevel configurationLevel) {
        return getFor(Set.of(key), configurationLevel).get(key);
    }

    public Map<ConfigurationKeys, MaybeConfiguration> getFor(Collection<ConfigurationKeys> keys, ConfigurationLevel configurationLevel) {
        var keysAsString = keys.stream().map(ConfigurationKeys::getValue).collect(Collectors.toSet());
        List<ConfigurationKeyValuePathLevel> found; // waiting for switch expressions...
        switch(configurationLevel.getPathLevel()) {
            case SYSTEM:
                found = configurationRepository.findByKeysAtSystemLevel(keysAsString);
                break;
            case ORGANIZATION:
                found = configurationRepository.findByOrganizationAndKeys(((OrganizationLevel)configurationLevel).organizationId, keysAsString);
                break;
            case EVENT:
                var eventLevel = (EventLevel) configurationLevel;
                found = configurationRepository.findByEventAndKeys(eventLevel.organizationId, eventLevel.eventId, keysAsString);
                break;
            case TICKET_CATEGORY:
                var categoryLevel = (CategoryLevel) configurationLevel;
                found = configurationRepository.findByTicketCategoryAndKeys(categoryLevel.organizationId, categoryLevel.eventId, categoryLevel.categoryId, keysAsString);
                break;
            default:
                found = List.of();
        }

        return buildKeyConfigurationMapResult(keys, found);
    }

    private Map<ConfigurationKeys, MaybeConfiguration> buildKeyConfigurationMapResult(Collection<ConfigurationKeys> keys, List<ConfigurationKeyValuePathLevel> found) {
        var res = new EnumMap<ConfigurationKeys, MaybeConfiguration>(ConfigurationKeys.class);

        for (var k : keys) {
            res.put(k, new MaybeConfiguration(k));
        }

        for (var c : found) {
            res.get(c.getConfigurationKey()).ifPresentOrElse(alreadyPresent -> {
                //override mechanism, if a configuration path is more precise that the one already present, we will replace it
                if (alreadyPresent.getConfigurationPathLevel().getPriority() < c.getConfigurationPathLevel().getPriority()) {
                    res.put(c.getConfigurationKey(), new MaybeConfiguration(c.getConfigurationKey(), c));
                }
            }, () -> res.put(c.getConfigurationKey(), new MaybeConfiguration(c.getConfigurationKey(), c)));
        }

        return res;
    }

    public static class MaybeConfiguration {
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        private final Optional<ConfigurationKeyValuePathLevel> configuration;
        private final ConfigurationKeys key;

        public MaybeConfiguration(ConfigurationKeys key) {
            this.key = key;
            this.configuration = Optional.empty();
        }

        public MaybeConfiguration(ConfigurationKeys key, ConfigurationKeyValuePathLevel configuration) {
            this.configuration = Optional.ofNullable(configuration);
            this.key = key;
        }

        void ifPresentOrElse(Consumer<? super ConfigurationKeyValuePathLevel> action, Runnable emptyAction) {
            configuration.ifPresentOrElse(action, emptyAction);
        }

        public boolean isPresent() {
            return configuration.isPresent();
        }

        public Optional<String> getValue() {
            return configuration.map(ConfigurationKeyValuePathLevel::getValue);
        }

        public String getValueOrDefault(String defaultValue) {
            return getValue().orElse(defaultValue);
        }

        public boolean getValueAsBooleanOrDefault(boolean defaultValue) {
            return getValue().map(Boolean::parseBoolean).orElse(defaultValue);
        }

        public int getValueAsIntOrDefault(int defaultValue) {
            return getValue().flatMap(v -> {
                try {
                    return Optional.of(Integer.parseInt(v));
                } catch(NumberFormatException ex) {
                    return Optional.empty();
                }
            }).orElse(defaultValue);
        }

        public ConfigurationPathLevel getConfigurationPathLevelOrDefault(ConfigurationPathLevel defaultValue) {
            return configuration.map(ConfigurationKeyValuePathLevel::getConfigurationPathLevel).orElse(defaultValue);
        }

        public String getRequiredValue() {
            return getValue().orElseThrow(() -> new IllegalArgumentException("Mandatory configuration key " + key + " not present"));
        }
    }

}
