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

import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.modification.ConfigurationModification;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.*;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.ConfigurationPathLevel;
import alfio.model.user.User;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH;
import static alfio.model.system.ConfigurationPathLevel.EVENT;
import static alfio.model.system.ConfigurationPathLevel.ORGANIZATION;
import static alfio.model.system.ConfigurationPathLevel.SYSTEM;
import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional
@Log4j2
public class ConfigurationManager {

    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> ORGANIZATION_CONFIGURATION = collectConfigurationKeysByCategory(ORGANIZATION);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> EVENT_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.EVENT);
    private static final Map<ConfigurationKeys.SettingCategory, List<Configuration>> CATEGORY_CONFIGURATION = collectConfigurationKeysByCategory(ConfigurationPathLevel.TICKET_CATEGORY);

    private static final Predicate<ConfigurationModification> TO_BE_SAVED = c -> Optional.ofNullable(c.getId()).orElse(-1) > -1 || !StringUtils.isBlank(c.getValue());


    private final ConfigurationRepository configurationRepository;
    private final UserManager userManager;
    private final EventRepository eventRepository;

    @Autowired
    public ConfigurationManager(ConfigurationRepository configurationRepository,
                                UserManager userManager,
                                EventRepository eventRepository) {
        this.configurationRepository = configurationRepository;
        this.userManager = userManager;
        this.eventRepository = eventRepository;
    }

    //TODO: refactor, not the most beautiful code, find a better solution...
    private Configuration findByConfigurationPathAndKey(ConfigurationPath path, ConfigurationKeys key) {
        switch (path.pathLevel()) {
            case SYSTEM: return configurationRepository.findByKey(key.getValue());
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
    private Configuration selectPath(List<Configuration> conf) {
        return conf.size() == 1 ? conf.get(0) : conf.stream()
            .sorted(Comparator.comparing(Configuration::getConfigurationPathLevel).reversed())
            .findFirst().orElse(null);
    }

    //meh
    @SuppressWarnings("unchecked")
    private static <T> T from(ConfigurationPath c) {
        return (T) c;
    }

    public int getIntConfigValue(ConfigurationPathKey pathKey, int defaultValue) {
        try {
            return Optional.ofNullable(findByConfigurationPathAndKey(pathKey.getPath(), pathKey.getKey()))
                .map(Configuration::getValue)
                .map(Integer::parseInt).orElse(defaultValue);
        } catch (NumberFormatException | EmptyResultDataAccessException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanConfigValue(ConfigurationPathKey pathKey, boolean defaultValue) {
        return getStringConfigValue(pathKey)
            .map(Boolean::parseBoolean)
            .orElse(defaultValue);
    }


    public String getStringConfigValue(ConfigurationPathKey pathKey, String defaultValue) {
        return getStringConfigValue(pathKey).orElse(defaultValue);
    }

    public Optional<String> getStringConfigValue(ConfigurationPathKey pathKey) {
        return optionally(() -> findByConfigurationPathAndKey(pathKey.getPath(), pathKey.getKey())).map(Configuration::getValue);
    }

    public String getRequiredValue(ConfigurationPathKey pathKey) {
        return getStringConfigValue(pathKey)
            .orElseThrow(() -> new IllegalArgumentException("Mandatory configuration key " + pathKey.getKey() + " not present"));
    }

    // begin SYSTEM related configuration methods

    public void saveAllSystemConfiguration(List<ConfigurationModification> list) {
        list.forEach(c -> saveSystemConfiguration(ConfigurationKeys.fromString(c.getKey()), c.getValue()));
    }

    public void saveAllOrganizationConfiguration(int organizationId, List<ConfigurationModification> list, String username) {
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), organizationId), "Cannot update settings, user is not owner");
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> {
                Optional<String> value = evaluateValue(c.getKey(), c.getValue());
                Optional<Configuration> existing = configurationRepository.findByKeyAtOrganizationLevel(organizationId, c.getKey());
                if (!value.isPresent()) {
                    configurationRepository.deleteOrganizationLevelByKey(c.getKey(), organizationId);
                } else if (existing.isPresent()) {
                    configurationRepository.updateOrganizationLevel(organizationId, c.getKey(), value.get());
                } else {
                    configurationRepository.insertOrganizationLevel(organizationId, c.getKey(), value.get(), ConfigurationKeys.fromString(c.getKey()).getDescription());
                }
            });
    }

    public void saveEventConfiguration(int eventId, int organizationId, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        Validate.isTrue(userManager.isOwnerOfOrganization(user, organizationId), "Cannot update settings, user is not owner");
        Event event = eventRepository.findById(eventId);
        Validate.notNull(event, "event does not exist");
        if(organizationId != event.getOrganizationId()) {
            Validate.isTrue(userManager.isOwnerOfOrganization(user, event.getOrganizationId()), "Cannot update settings, user is not owner of event");
        }
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> {
                Optional<Configuration> existing = configurationRepository.findByKeyAtEventLevel(eventId, organizationId, c.getKey());
                Optional<String> value = evaluateValue(c.getKey(), c.getValue());
                if(!value.isPresent()) {
                    configurationRepository.deleteEventLevelByKey(c.getKey(), eventId);
                } else if (existing.isPresent()) {
                    configurationRepository.updateEventLevel(eventId, organizationId, c.getKey(), value.get());
                } else {
                    configurationRepository.insertEventLevel(organizationId, eventId, c.getKey(), value.get(), ConfigurationKeys.fromString(c.getKey()).getDescription());
                }
            });
    }

    public void saveCategoryConfiguration(int categoryId, int eventId, List<ConfigurationModification> list, String username) {
        User user = userManager.findUserByUsername(username);
        Event event = eventRepository.findById(eventId);
        Validate.notNull(event, "event does not exist");
        Validate.isTrue(userManager.isOwnerOfOrganization(user, event.getOrganizationId()), "Cannot update settings, user is not owner of event");
        list.stream()
            .filter(TO_BE_SAVED)
            .forEach(c -> {
                Optional<Configuration> existing = configurationRepository.findByKeyAtCategoryLevel(eventId, event.getOrganizationId(), categoryId, c.getKey());
                Optional<String> value = evaluateValue(c.getKey(), c.getValue());
                if(!value.isPresent()) {
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
        Optional<Configuration> conf = optionally(() -> findByConfigurationPathAndKey(Configuration.system(), key));
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
            if(!conf.isPresent()) {
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
                boolean absent = !configurationRepository.findOptionalByKey(key.getValue()).isPresent();
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
        return groupByCategory(isAdmin ? union(SYSTEM, ORGANIZATION) : ORGANIZATION_CONFIGURATION, existing);
    }

    public Map<ConfigurationKeys.SettingCategory, List<Configuration>> loadEventConfig(int eventId, String username) {
        User user = userManager.findUserByUsername(username);
        Event event = eventRepository.findById(eventId);
        int organizationId = event.getOrganizationId();
        if(!userManager.isOwnerOfOrganization(user, organizationId)) {
            return Collections.emptyMap();
        }
        boolean isAdmin = userManager.isAdmin(user);
        Map<ConfigurationKeys.SettingCategory, List<Configuration>> existing = configurationRepository.findEventConfiguration(organizationId, eventId).stream().filter(checkActualConfigurationLevel(isAdmin, EVENT)).sorted().collect(groupByCategory());
        return groupByCategory(isAdmin ? union(SYSTEM, EVENT) : EVENT_CONFIGURATION, existing);
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
        Event event = eventRepository.findById(eventId);
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
                Set<Configuration> entries = new TreeSet<>();
                ConfigurationKeys.SettingCategory key = e.getKey();
                entries.addAll(e.getValue());
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
        return k -> new Configuration(-1, k.getValue(), null, k.getDescription(), level);
    }

    public void deleteKey(String key) {
        configurationRepository.deleteByKey(key);
    }

    public void deleteOrganizationLevelByKey(String key, int organizationId, String username) {
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), organizationId), "User is not owner of the organization. Therefore, delete is not allowed.");
        configurationRepository.deleteOrganizationLevelByKey(key, organizationId);
    }

    public void deleteEventLevelByKey(String key, int eventId, String username) {
        Event event = eventRepository.findById(eventId);
        Validate.notNull(event, "Wrong event id");
        Validate.isTrue(userManager.isOwnerOfOrganization(userManager.findUserByUsername(username), event.getOrganizationId()), "User is not owner of the organization. Therefore, delete is not allowed.");
        configurationRepository.deleteEventLevelByKey(key, eventId);
    }

    public void deleteCategoryLevelByKey(String key, int eventId, int categoryId, String username) {
        Event event = eventRepository.findById(eventId);
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

    public String getShortReservationID(Event event, String reservationId) {
        return StringUtils.substring(reservationId, 0, getIntConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), PARTIAL_RESERVATION_ID_LENGTH), 8)).toUpperCase();
    }
}
