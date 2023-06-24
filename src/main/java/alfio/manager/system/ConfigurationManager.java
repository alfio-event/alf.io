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

import alfio.config.Initializer;
import alfio.controller.api.v2.model.AlfioInfo;
import alfio.controller.api.v2.model.AnalyticsConfiguration;
import alfio.controller.api.v2.model.WalletConfiguration;
import alfio.controller.api.v2.user.support.PurchaseContextInfoBuilder;
import alfio.manager.system.ConfigurationLevels.CategoryLevel;
import alfio.manager.system.ConfigurationLevels.EventLevel;
import alfio.manager.system.ConfigurationLevels.OrganizationLevel;
import alfio.manager.system.ConfigurationLevels.SubscriptionDescriptorLevel;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.modification.ConfigurationModification;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.*;
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.*;
import static alfio.model.system.ConfigurationPathLevel.*;
import static java.util.stream.Collectors.toList;

@Transactional
@Log4j2
@RequiredArgsConstructor
public class ConfigurationManager {

    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> ORGANIZATION_CONFIGURATION = collectConfigurationKeysByCategory(ORGANIZATION);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> PURCHASE_CONTEXT_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.PURCHASE_CONTEXT);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> CATEGORY_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.TICKET_CATEGORY);

    private final ConfigurationRepository configurationRepository;
    private final UserManager userManager;
    private final EventRepository eventRepository;
    private final ExternalConfiguration externalConfiguration;
    private final Environment environment;
    private final Cache<Set<ConfigurationKeys>, Map<ConfigurationKeys, MaybeConfiguration>> oneMinuteCache;
    private final SecureRandom secureRandom = new SecureRandom();

    //TODO: refactor, not the most beautiful code, find a better solution...
    private Optional<Configuration> findByConfigurationPathAndKey(ConfigurationPath path, ConfigurationKeys key) {
        var keyAsString = key.getValue();
        var configList = new ArrayList<>(externalConfiguration.load(keyAsString));
        switch (path.pathLevel()) {
            case SYSTEM:
                configList.addAll(configurationRepository.findByKeyAtSystemLevel(keyAsString));
                return selectPath(configList);
            case ORGANIZATION: {
                OrganizationConfigurationPath o = (OrganizationConfigurationPath) path;
                configList.addAll(configurationRepository.findByOrganizationAndKey(o.getId(), key.getValue()));
                return selectPath(configList);
            }
            case PURCHASE_CONTEXT: {
                if (path instanceof EventConfigurationPath) {
                    EventConfigurationPath o = (EventConfigurationPath) path;
                    configList.addAll(configurationRepository.findByEventAndKey(o.getOrganizationId(),
                        o.getId(), keyAsString));
                } else {
                    SubscriptionDescriptorConfigurationPath o = (SubscriptionDescriptorConfigurationPath) path;
                    configList.addAll(configurationRepository.findBySubscriptionDescriptorAndKey(o.getOrganizationId(), o.getId(), keyAsString));
                }
                return selectPath(configList);
            }
            case TICKET_CATEGORY: {
                TicketCategoryConfigurationPath o = (TicketCategoryConfigurationPath) path;
                configList.addAll(configurationRepository.findByTicketCategoryAndKey(o.getOrganizationId(),
                    o.getEventId(), o.getId(), keyAsString));
                return selectPath(configList);
            }
            default:
                throw new IllegalStateException("Can't reach here");
        }
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
            case PURCHASE_CONTEXT:
                if (path instanceof EventConfigurationPath) {
                    var eventPath = (EventConfigurationPath) path;
                    saveEventConfiguration(eventPath.getId(), eventPath.getOrganizationId(), pathKey.getKey().name(), value);
                } else {
                    var subscriptionDescriptorPath = (SubscriptionDescriptorConfigurationPath) path;
                    saveSubscriptionDescriptorConfiguration(subscriptionDescriptorPath.getId(), subscriptionDescriptorPath.getOrganizationId(), pathKey.getKey().name(), value);
                }
                break;
            default:
                throw new IllegalStateException("can't reach here");
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
            .filter(ConfigurationManager::toBeSaved)
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

    private void saveSubscriptionDescriptorConfiguration(UUID id, int organizationId, String key, String optionValue) {
        Optional<Configuration> existing = configurationRepository.findByKeyAtSubscriptionDescriptorLevel(id, organizationId, key);
        Optional<String> value = evaluateValue(key, optionValue);
        if(value.isEmpty()) {
            configurationRepository.deleteSubscriptionDescriptorLevelByKey(key, id);
        } else if (existing.isPresent()) {
            configurationRepository.updateSubscriptionDescriptorLevel(id, organizationId, key, value.get());
        } else {
            configurationRepository.insertSubscriptionDescriptorLevel(organizationId, id, key, value.get(), ConfigurationKeys.fromString(key).getDescription());
        }
    }

    public void saveAllSubscriptionDescriptorConfiguration(SubscriptionDescriptor sd, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        Validate.isTrue(userManager.isOwnerOfOrganization(user, sd.getOrganizationId()), "Cannot update settings, user is not owner");
        list.stream()
            .filter(ConfigurationManager::toBeSaved)
            .forEach(c -> saveSubscriptionDescriptorConfiguration(sd.getId(), sd.getOrganizationId(), c.getKey(), c.getValue()));
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
            .filter(ConfigurationManager::toBeSaved)
            .forEach(c -> saveEventConfiguration(eventId, organizationId, c.getKey(), c.getValue()));
    }

    public void saveCategoryConfiguration(int categoryId, int eventId, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        Validate.notNull(event, "event does not exist");
        Validate.isTrue(userManager.isOwnerOfOrganization(user, event.getOrganizationId()), "Cannot update settings, user is not owner of event");
        list.stream()
            .filter(ConfigurationManager::toBeSaved)
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
            if(conf.filter(c -> c.getConfigurationPathLevel() != EXTERNAL).isPresent()) {
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
            if(conf.isEmpty() || conf.get().getConfigurationPathLevel() == EXTERNAL) {
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
                boolean absent = externalConfiguration.getSingle(key.getValue())
                    .or(() -> configurationRepository.findOptionalByKey(key.getValue())).isEmpty();
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
            .collect(toList());

        if(toBeRemoved.isEmpty()) {
            return result;
        } else {
            return result.entrySet().stream()
                .filter(entry -> !toBeRemoved.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    public String getSingleConfigForOrganization(int organizationId, String keyAsString, String username) {
        User user = userManager.findUserByUsername(username);
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return null;
        }
        var key = safeValueOf(keyAsString);
        return getFirstConfigurationResult(configurationRepository.findByOrganizationAndKey(organizationId, key.name()), keyAsString);
    }

    public String getSingleConfigForEvent(int eventId, String keyAsString, String username) {
        User user = userManager.findUserByUsername(username);
        EventAndOrganizationId event = eventRepository.findEventAndOrganizationIdById(eventId);
        int organizationId = event.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return null;
        }
        var key = safeValueOf(keyAsString);
        return getFirstConfigurationResult(configurationRepository.findByEventAndKey(organizationId, eventId, key.name()), keyAsString);
    }

    private String getFirstConfigurationResult(List<Configuration> results, String keyAsString) {
        return Objects.requireNonNull(results).stream()
            .findFirst()
            .map(Configuration::getValue)
            .or(() -> externalConfiguration.getSingle(keyAsString).map(Configuration::getValue))
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
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findEventConfiguration(organizationId, eventId).stream().filter(checkActualConfigurationLevel(isAdmin, PURCHASE_CONTEXT)).sorted().collect(groupByCategory());
        boolean offlineCheckInEnabled = areBooleanSettingsEnabledForEvent(ALFIO_PI_INTEGRATION_ENABLED, OFFLINE_CHECKIN_ENABLED).test(event);
        return removeAlfioPISettingsIfNeeded(offlineCheckInEnabled, groupByCategory(isAdmin ? union(SYSTEM, PURCHASE_CONTEXT) : PURCHASE_CONTEXT_CONFIGURATION, existing));
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadSubscriptionDescriptorConfig(SubscriptionDescriptor subscriptionDescriptor, String username) {
        User user = userManager.findUserByUsername(username);
        int organizationId = subscriptionDescriptor.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return Collections.emptyMap();
        }
        boolean isAdmin = userManager.isAdmin(user);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findSubscriptionDescriptorConfiguration(organizationId, subscriptionDescriptor.getId()).stream().filter(checkActualConfigurationLevel(isAdmin, PURCHASE_CONTEXT)).sorted().collect(groupByCategory());
        return groupByCategory(isAdmin ? union(SYSTEM, PURCHASE_CONTEXT) : PURCHASE_CONTEXT_CONFIGURATION, existing);
    }

    public Predicate<EventAndOrganizationId> areBooleanSettingsEnabledForEvent(ConfigurationKeys... keys) {
        return event -> getFor(Set.of(keys), event.getConfigurationLevel()).entrySet().stream().allMatch(kv -> kv.getValue().getValueAsBooleanOrDefault());
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
            .collect(ArrayList::new, (List<Configuration> list, Configuration conf) -> {
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
                    List<Configuration> configurations = existing.get(key).stream().filter(Predicate.not(Configuration::isInternal)).collect(Collectors.toList());
                    configurations.forEach(entries::remove);
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
                .collect(toList());
        final List<Configuration> missing = Arrays.stream(ConfigurationKeys.visible())
                .filter(k -> existing.stream().noneMatch(c -> c.getKey().equals(k.getValue())))
                .map(mapEmptyKeys(ConfigurationPathLevel.SYSTEM))
                .collect(toList());
        List<Configuration> result = new ArrayList<>(existing);
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

    public String getShortReservationID(Configurable configurable, TicketReservation reservation) {
        var conf = getFor(Set.of(USE_INVOICE_NUMBER_AS_ID, PARTIAL_RESERVATION_ID_LENGTH), configurable.getConfigurationLevel());
        if(conf.get(USE_INVOICE_NUMBER_AS_ID).getValueAsBooleanOrDefault() && reservation.getHasInvoiceNumber()) {
            return reservation.getInvoiceNumber();
        }
        return StringUtils.substring(reservation.getId(), 0, conf.get(PARTIAL_RESERVATION_ID_LENGTH).getValueAsIntOrDefault(8)).toUpperCase();
    }

    public String getPublicReservationID(Configurable configurable, TicketReservation reservation) {
        if(getFor(USE_INVOICE_NUMBER_AS_ID, configurable.getConfigurationLevel()).getValueAsBooleanOrDefault() && reservation.getHasInvoiceNumber()) {
            return reservation.getInvoiceNumber();
        }
        return reservation.getId();
    }

    public boolean hasAllConfigurationsForInvoice(Configurable configurable) {
        var r = getFor(Set.of(INVOICE_ADDRESS, VAT_NR), configurable.getConfigurationLevel());
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
        return configurationValues.get(ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS).getValueAsBooleanOrDefault() &&
            configurationValues.get(RECAPTCHA_API_KEY).getValueOrNull() != null;
    }

    public boolean isRecaptchaForOfflinePaymentAndFreeEnabled(ConfigurationLevel configurationLevel) {
        var conf = getFor(Set.of(ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS, RECAPTCHA_API_KEY), configurationLevel);
        return isRecaptchaForOfflinePaymentAndFreeEnabled(conf);
    }

    // https://github.com/alfio-event/alf.io/issues/573

    public boolean canAttachBillingDocumentToConfirmationEmail(Configurable configurable) {
        var config = getFor(List.of(ENABLE_ITALY_E_INVOICING, ITALY_E_INVOICING_SEND_PROFORMA), configurable.getConfigurationLevel());
        return !isItalianEInvoicingEnabled(config)
            || config.get(ITALY_E_INVOICING_SEND_PROFORMA).getValueAsBooleanOrDefault();
    }

    public boolean canGenerateReceiptOrInvoiceToCustomer(Configurable configurable) {
        return !isItalianEInvoicingEnabled(configurable);
    }

    public boolean canGenerateReceiptOrInvoiceToCustomer(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        return !isItalianEInvoicingEnabled(configurationValues);
    }

    public boolean isInvoiceOnly(Configurable configurable) {
        var res = getFor(Set.of(GENERATE_ONLY_INVOICE, ENABLE_ITALY_E_INVOICING), configurable.getConfigurationLevel());
        return isInvoiceOnly(res);
    }

    /**
     * @param configurationValues note: require the key GENERATE_ONLY_INVOICE and ENABLE_ITALY_E_INVOICING to be present
     * @return
     */
    public boolean isInvoiceOnly(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(GENERATE_ONLY_INVOICE) && configurationValues.containsKey(ENABLE_ITALY_E_INVOICING));
        return configurationValues.get(GENERATE_ONLY_INVOICE).getValueAsBooleanOrDefault() || configurationValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault();
    }

    public boolean isItalianEInvoicingEnabled(Configurable configurable) {
        return isItalianEInvoicingEnabled(configurable.getConfigurationLevel());
    }

    public boolean isItalianEInvoicingEnabled(ConfigurationLevel configurationLevel) {
        var res = getFor(List.of(ENABLE_ITALY_E_INVOICING), configurationLevel);
        return isItalianEInvoicingEnabled(res);
    }

    public boolean isItalianEInvoicingEnabled(Map<ConfigurationKeys, MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_ITALY_E_INVOICING));
        return configurationValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault();
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
        List<ConfigurationKeyValuePathLevel> found = new ArrayList<>(externalConfiguration.getAll(keysAsString));
        switch(configurationLevel.getPathLevel()) {
            case SYSTEM:
                found.addAll(configurationRepository.findByKeysAtSystemLevel(keysAsString));
                break;
            case ORGANIZATION:
                found.addAll(configurationRepository.findByOrganizationAndKeys(((OrganizationLevel)configurationLevel).organizationId, keysAsString));
                break;
            case PURCHASE_CONTEXT:
                if (configurationLevel instanceof EventLevel) {
                    // event
                    var eventLevel = (EventLevel) configurationLevel;
                    found.addAll(configurationRepository.findByEventAndKeys(eventLevel.organizationId, eventLevel.eventId, keysAsString));
                } else {
                    var subscriptionDescriptorLevel = (SubscriptionDescriptorLevel) configurationLevel;
                    found.addAll(configurationRepository.findBySubscriptionDescriptorAndKeys(subscriptionDescriptorLevel.organizationId, subscriptionDescriptorLevel.subscriptionDescriptorId, keysAsString));
                }
                break;
            case TICKET_CATEGORY:
                var categoryLevel = (CategoryLevel) configurationLevel;
                found.addAll(configurationRepository.findByTicketCategoryAndKeys(categoryLevel.organizationId, categoryLevel.eventId, categoryLevel.categoryId, keysAsString));
                break;
            default:
                break;
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

    public List<PaymentMethod> getBlacklistedMethodsForReservation(PurchaseContext p, Collection<Integer> categoryIds) {
        return p.event().map(e -> {
            if(categoryIds.size() > 1) {
                Map<Integer, String> blacklistForCategories = configurationRepository.getAllCategoriesAndValueWith(e.getOrganizationId(), e.getId(), PAYMENT_METHODS_BLACKLIST);
                return categoryIds.stream()
                    .filter(blacklistForCategories::containsKey)
                    .flatMap(id -> Arrays.stream(blacklistForCategories.get(id).split(",")))
                    .filter(StringUtils::isNotBlank)
                    .map(name -> PaymentProxy.valueOf(name).getPaymentMethod())
                    .collect(toList());
            } else if (!categoryIds.isEmpty()) {
                    return configurationRepository.findByKeyAtCategoryLevel(e.getId(), e.getOrganizationId(), IterableUtils.get(categoryIds, 0), PAYMENT_METHODS_BLACKLIST.name())
                        .filter(v -> StringUtils.isNotBlank(v.getValue()))
                        .map(v -> Arrays.stream(v.getValue().split(",")).map(name -> PaymentProxy.valueOf(name).getPaymentMethod()).collect(toList()))
                        .orElse(List.of());
            } else {
                return List.<PaymentMethod>of();
            }
        }).orElse(List.of());
    }

    private static boolean toBeSaved(ConfigurationModification c) {
        return Optional.ofNullable(c.getId()).orElse(-1) > -1 || !StringUtils.isBlank(c.getValue());
    }

    public List<Integer> getCategoriesWithNoTaxes(List<Integer> categoriesIds) {
        if (categoriesIds.isEmpty()) {
            return List.of();
        }
        return configurationRepository.getCategoriesWithFlag(categoriesIds, APPLY_TAX_TO_CATEGORY.name(), BooleanUtils.FALSE);
    }

    public boolean noTaxesFlagDefinedFor(List<TicketCategory> categories) {
        return !getCategoriesWithNoTaxes(categories.stream().map(TicketCategory::getId).collect(toList())).isEmpty();
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

        public boolean isEmpty() {
            return configuration.isEmpty();
        }

        public Optional<String> getValue() {
            return configuration.map(ConfigurationKeyValuePathLevel::getValue);
        }

        public String getValueOrNull() {
            return getValue().orElse(null);
        }

        public String getValueOrDefault(String defaultValue) {
            return getValue().orElse(defaultValue);
        }

        public boolean getValueAsBooleanOrDefault() {
            return getValue().map(Boolean::parseBoolean)
                .orElseGet(() -> Boolean.parseBoolean(Objects.requireNonNull(key.getDefaultValue())));
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

    /**
     * Fetch all ticket_category_id, value that are present at the ticket category level with a given configuration key
     *
     * @param event
     * @param key
     * @return
     */
    public Map<Integer, String> getAllCategoriesAndValueWith(EventAndOrganizationId event, ConfigurationKeys key) {
        return configurationRepository.getAllCategoriesAndValueWith(event.getOrganizationId(), event.getId(), key);
    }

    public AlfioInfo getInfo(HttpSession session) {
        var demoMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO));
        var devMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEV));
        var prodMode = environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_LIVE));


        var options = EnumSet.of(
            GOOGLE_ANALYTICS_ANONYMOUS_MODE,
            GOOGLE_ANALYTICS_KEY,
            GLOBAL_PRIVACY_POLICY,
            GLOBAL_TERMS,
            ENABLE_ITALY_E_INVOICING,
            ENABLE_CUSTOMER_REFERENCE,
            VAT_NUMBER_IS_REQUIRED,
            GENERATE_ONLY_INVOICE,
            INVOICE_ADDRESS,
            VAT_NR,
            ENABLE_EU_VAT_DIRECTIVE,
            COUNTRY_OF_BUSINESS,
            ENABLE_REVERSE_CHARGE_IN_PERSON,
            ENABLE_REVERSE_CHARGE_ONLINE,
            ANNOUNCEMENT_BANNER_CONTENT,
            ENABLE_WALLET,
            ENABLE_PASS);
        var conf = getFor(options, ConfigurationLevel.system());

        var analyticsConf = AnalyticsConfiguration.build(conf, session);

        return new AlfioInfo(demoMode,
            devMode,
            prodMode,
            analyticsConf,
            conf.get(GLOBAL_PRIVACY_POLICY).getValueOrNull(),
            conf.get(GLOBAL_TERMS).getValueOrNull(),
            PurchaseContextInfoBuilder.invoicingInfo(this, conf),
            StringUtils.trimToNull(conf.get(ANNOUNCEMENT_BANNER_CONTENT).getValueOrNull()),
            new WalletConfiguration(conf.get(ENABLE_WALLET).getValueAsBooleanOrDefault(), conf.get(ENABLE_PASS).getValueAsBooleanOrDefault()));
    }

    public Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> getPublicOpenIdConfiguration() {
        return oneMinuteCache.get(EnumSet.of(OPENID_PUBLIC_ENABLED, OPENID_CONFIGURATION_JSON),
            k -> getFor(k, ConfigurationLevel.system()));
    }

    public boolean isPublicOpenIdEnabled() {
        return getPublicOpenIdConfiguration().get(OPENID_PUBLIC_ENABLED).getValueAsBooleanOrDefault();
    }

    public String baseUrl(PurchaseContext purchaseContext) {
        var configurationLevel = purchaseContext.event().map(ConfigurationLevel::event)
            .orElseGet(() -> ConfigurationLevel.organization(purchaseContext.getOrganizationId()));
        return StringUtils.removeEnd(getFor(BASE_URL, configurationLevel).getRequiredValue(), "/");
    }

    public String retrieveSystemApiKey(boolean rotate) {
        Optional<Configuration> existing = configurationRepository.findOptionalByKey(SYSTEM_API_KEY.name());
        String apiKeyValue;
        if(existing.isPresent() && rotate) {
            apiKeyValue = generateApiKey();
            configurationRepository.update(SYSTEM_API_KEY.name(), apiKeyValue);
        } else if(existing.isPresent()) {
            apiKeyValue = existing.get().getValue();
        } else {
            apiKeyValue = generateApiKey();
            configurationRepository.insert(SYSTEM_API_KEY.name(), apiKeyValue, SYSTEM_API_KEY.getDescription());
        }
        return apiKeyValue;
    }

    private String generateApiKey() {
        var bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return new BigInteger(1, bytes).toString(36);
    }
}
