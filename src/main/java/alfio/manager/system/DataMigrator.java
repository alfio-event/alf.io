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

import alfio.model.Event;
import alfio.model.system.ConfigurationKeys;
import alfio.model.system.EventMigration;
import alfio.repository.EventRepository;
import alfio.repository.TicketRepository;
import alfio.repository.plugin.PluginConfigurationRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.repository.system.EventMigrationRepository;
import alfio.util.EventUtil;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static alfio.util.OptionalWrapper.optionally;

@Component
@Transactional(readOnly = true)
@Log4j2
public class DataMigrator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d\\.)([0-9\\.]*)(-SNAPSHOT)?");
    private final EventMigrationRepository eventMigrationRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final BigDecimal currentVersion;
    private final String currentVersionAsString;
    private final ZonedDateTime buildTimestamp;
    private final TransactionTemplate transactionTemplate;
    private final ConfigurationRepository configurationRepository;
    private final PluginConfigurationRepository pluginConfigurationRepository;
    private final NamedParameterJdbcTemplate jdbc;

    @Autowired
    public DataMigrator(EventMigrationRepository eventMigrationRepository,
                        EventRepository eventRepository,
                        @Value("${alfio.version}") String currentVersion,
                        @Value("${alfio.build-ts}") String buildTimestamp,
                        PlatformTransactionManager transactionManager,
                        TicketRepository ticketRepository,
                        ConfigurationRepository configurationRepository,
                        PluginConfigurationRepository pluginConfigurationRepository, NamedParameterJdbcTemplate jdbc) {
        this.eventMigrationRepository = eventMigrationRepository;
        this.eventRepository = eventRepository;
        this.ticketRepository = ticketRepository;
        this.configurationRepository = configurationRepository;
        this.pluginConfigurationRepository = pluginConfigurationRepository;
        this.jdbc = jdbc;
        this.currentVersion = parseVersion(currentVersion);
        this.currentVersionAsString = currentVersion;
        this.buildTimestamp = ZonedDateTime.parse(buildTimestamp);
        this.transactionTemplate = new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    }

    public void migrateEventsToCurrentVersion() {
        eventRepository.findAll().stream()
                .filter(e -> ZonedDateTime.now(e.getZoneId()).isBefore(e.getEnd()))
                .forEach(this::migrateEventToCurrentVersion);
        fillReservationsLanguage();
        fillDefaultOptions();
    }

    private void fillDefaultOptions() {
        transactionTemplate.execute(ts -> {
            int count = jdbc.queryForObject("select count(*) from configuration where c_key = :key", new MapSqlParameterSource("key", ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue()), Integer.class);
            if(count == 0) {
                configurationRepository.insert(ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getValue(), "true", ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE.getDescription());
            }
            return null;
        });
    }

    void migrateEventToCurrentVersion(Event event) {
        Optional<EventMigration> optional = optionally(() -> eventMigrationRepository.loadEventMigration(event.getId()));
        boolean alreadyDefined = optional.isPresent();
        if(!alreadyDefined || optional.filter(this::needsFixing).isPresent()) {
            transactionTemplate.execute(s -> {
                optional.ifPresent(eventMigration -> eventMigrationRepository.lockEventMigrationForUpdate(eventMigration.getId()));
                createMissingTickets(event);
                fillDescriptions(event);
                migratePluginConfig(event);
                if(alreadyDefined) {
                    EventMigration eventMigration = optional.get();
                    int result = eventMigrationRepository.updateMigrationData(eventMigration.getId(), currentVersionAsString, buildTimestamp, EventMigration.Status.COMPLETE.name());
                    Validate.isTrue(result == 1, "error during update " + result);
                } else {
                    eventMigrationRepository.insertMigrationData(event.getId(), currentVersionAsString, buildTimestamp, EventMigration.Status.COMPLETE.name());
                }

                return null;
            });
        }
    }

    void migratePluginConfig(Event event) {
        transactionTemplate.execute(s -> {
            pluginConfigurationRepository.loadByEventId(-1).forEach(p -> {
                MapSqlParameterSource source = new MapSqlParameterSource("pluginId", p.getPluginId())
                    .addValue("eventId", event.getId())
                    .addValue("confName", p.getOptionName())
                    .addValue("confValue", p.getValue())
                    .addValue("description", p.getDescription())
                    .addValue("confType", p.getComponentType().name());
                jdbc.update("insert into plugin_configuration(plugin_id, event_id, conf_name, conf_value, conf_description, conf_type) values(:pluginId, :eventId, :confName, :confValue, :description, :confType)", source);
            });
            jdbc.update("update plugin_configuration set event_id = -2 where event_id = -1", new EmptySqlParameterSource());
            return null;
        });
    }

    void fillReservationsLanguage() {
        transactionTemplate.execute(s -> {
            jdbc.queryForList("select id from tickets_reservation where user_language is null", new EmptySqlParameterSource(), String.class)
                    .forEach(id -> {
                        MapSqlParameterSource param = new MapSqlParameterSource("reservationId", id);
                        String language = optionally(() -> jdbc.queryForObject("select user_language from ticket where tickets_reservation_id = :reservationId limit 1", param, String.class)).orElse("en");
                        jdbc.update("update tickets_reservation set user_language = :userLanguage where id = :reservationId", param.addValue("userLanguage", language));
                    });
            return null;
        });
    }

    private void fillDescriptions(Event event) {
        int result = eventRepository.fillDisplayNameIfRequired(event.getId());
        if(result > 0) {
            log.info("Event {} didn't have displayName, filled with shortName", event.getShortName());
        }
    }

    private void createMissingTickets(Event event) {
        int existingTickets = ticketRepository.countExistingTicketsForEvent(event.getId());
        if(existingTickets < event.getAvailableSeats()) {
            MapSqlParameterSource[] tickets = EventUtil.generateEmptyTickets(event, new Date(), event.getAvailableSeats() - existingTickets).toArray(MapSqlParameterSource[]::new);
            jdbc.batchUpdate(ticketRepository.bulkTicketInitialization(), tickets);
        }
    }

    boolean needsFixing(EventMigration eventMigration) {
        return eventMigration.getBuildTimestamp().isBefore(buildTimestamp) || parseVersion(eventMigration.getCurrentVersion()).compareTo(currentVersion) < 0;
    }

    static BigDecimal parseVersion(String version) {
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if(!matcher.find()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(matcher.group(1) + matcher.group(2).replaceAll("\\.", ""));
    }
}
