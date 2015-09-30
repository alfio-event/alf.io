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
package alfio.model.system;

import alfio.model.Event;
import alfio.model.TicketCategory;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Getter
public class Configuration {

    private final int id;
    private final String key;
    private final String value;
    private final String description;
    private final ConfigurationKeys configurationKey;
    private final ConfigurationPathLevel configurationPathLevel;
    private final boolean basic;


    public Configuration(@Column("id") int id,
                         @Column("c_key") String key,
                         @Column("c_value") String value,
                         @Column("description") String description,
                         @Column("configuration_path_level") ConfigurationPathLevel configurationPathLevel) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.description = description;
        this.configurationKey = ConfigurationKeys.valueOf(key);
        this.configurationPathLevel = configurationPathLevel;
        this.basic = this.configurationKey.isBasic();
    }

    public ComponentType getComponentType() {
        return configurationKey.getComponentType();
    }


    public interface ConfigurationPath {
        ConfigurationPathLevel pathLevel();
    }

    @EqualsAndHashCode
    public static class SystemConfigurationPath implements  ConfigurationPath {
        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.SYSTEM;
        }

    }

    @EqualsAndHashCode
    @Getter
    public static class OrganizationConfigurationPath implements ConfigurationPath {

        private final int id;

        private OrganizationConfigurationPath(int id) {
            this.id = id;
        }

        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.ORGANIZATION;
        }

    }

    @EqualsAndHashCode
    @Getter
    public static class EventConfigurationPath implements ConfigurationPath {

        private final int organizationId;
        private final int id;

        private EventConfigurationPath(int organizationId, int id) {
            this.organizationId = organizationId;
            this.id = id;
        }


        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.EVENT;
        }

    }

    @EqualsAndHashCode
    @Getter
    public static class TicketCategoryConfigurationPath implements ConfigurationPath {

        private final int organizationId;
        private final int eventId;
        private final int id;

        private TicketCategoryConfigurationPath(int organizationId, int eventId, int id) {
            this.organizationId = organizationId;
            this.eventId = eventId;
            this.id = id;
        }

        @Override
        public ConfigurationPathLevel pathLevel() {
            return ConfigurationPathLevel.TICKET_CATEGORY;
        }

    }

    public static ConfigurationPath system() {
        return new SystemConfigurationPath();
    }

    private static ConfigurationPath organization(int id) {
        return new OrganizationConfigurationPath(id);
    }

    private static ConfigurationPath event(Event event) {
        return new EventConfigurationPath(event.getOrganizationId(), event.getId());
    }

    private static ConfigurationPath ticketCategory(int organizationId, int eventId, int id) {
        return new TicketCategoryConfigurationPath(organizationId, eventId, id);
    }


    //
    @Getter
    @EqualsAndHashCode
    public static class ConfigurationPathKey {
        private final ConfigurationPath path;
        private final ConfigurationKeys key;

        private ConfigurationPathKey(ConfigurationPath path, ConfigurationKeys key) {
            this.path = path;
            this.key = key;
        }
    }
    //

    public static ConfigurationPathKey getSystemConfiguration(ConfigurationKeys configurationKey) {
        Validate.isTrue(configurationKey.supports(ConfigurationPathLevel.SYSTEM));
        return new ConfigurationPathKey(system(), configurationKey);
    }

    public static ConfigurationPathKey getOrganizationConfiguration(int organizationId, ConfigurationKeys configurationKey) {
        Validate.isTrue(configurationKey.supports(ConfigurationPathLevel.ORGANIZATION));
        return new ConfigurationPathKey(organization(organizationId), configurationKey);
    }

    public static ConfigurationPathKey getEventConfiguration(Event event, ConfigurationKeys configurationKey) {
        Validate.isTrue(configurationKey.supports(ConfigurationPathLevel.EVENT));
        return new ConfigurationPathKey(event(event), configurationKey);
    }

    public static ConfigurationPathKey getTicketCategoryConfiguration(Event event, TicketCategory ticketCategory, ConfigurationKeys configurationKey) {
        Validate.isTrue(configurationKey.supports(ConfigurationPathLevel.TICKET_CATEGORY));
        return new ConfigurationPathKey(ticketCategory(event.getOrganizationId(), event.getId(), ticketCategory.getId()), configurationKey);
    }

    public static ConfigurationPathKey initCompleted() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.INIT_COMPLETED);
    }

    public static ConfigurationPathKey baseUrl(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.BASE_URL);
    }

    public static ConfigurationPathKey vatNr() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.VAT_NR);
    }

    // --- google maps ---

    public static ConfigurationPathKey mapsServerApiKey() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.MAPS_SERVER_API_KEY);
    }

    public static ConfigurationPathKey mapsClientApiKey() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.MAPS_CLIENT_API_KEY);
    }

    // --- end google maps ---

    // --- stripe ---

    public static ConfigurationPathKey stripeSecretKey(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.STRIPE_SECRET_KEY);
    }

    public static ConfigurationPathKey stripePublicKey(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.STRIPE_PUBLIC_KEY);
    }

    // --- end stripe ---

    public static ConfigurationPathKey specialPriceCodeLength() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.SPECIAL_PRICE_CODE_LENGTH);
    }

    public static ConfigurationPathKey maxAmountOfTicketsByReservation(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAX_AMOUNT_OF_TICKETS_BY_RESERVATION);
    }

    public static ConfigurationPathKey assignmentReminderStart(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ASSIGNMENT_REMINDER_START);
    }

    public static ConfigurationPathKey assignmentReminderInterval(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ASSIGNMENT_REMINDER_INTERVAL);
    }

    public static ConfigurationPathKey reservationTimeout(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.RESERVATION_TIMEOUT);
    }

    public static ConfigurationPathKey mailerType(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAILER_TYPE);
    }

    public static ConfigurationPathKey maxEmailPerCycle() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.MAX_EMAIL_PER_CYCLE);
    }

    public static ConfigurationPathKey mailReplyTo(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAIL_REPLY_TO);
    }

    // --- smtp related ---

    public static ConfigurationPathKey smtpHost(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_HOST);
    }

    public static ConfigurationPathKey smtpPort(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_PORT);
    }

    public static ConfigurationPathKey smtpProtocol(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_PROTOCOL);
    }

    public static ConfigurationPathKey smtpUsername(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_USERNAME);
    }

    public static ConfigurationPathKey smtpPassword(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_PASSWORD);
    }

    public static ConfigurationPathKey smtpFromEmail(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_FROM_EMAIL);
    }

    public static ConfigurationPathKey smtpProperties(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.SMTP_PROPERTIES);
    }

    // --- end smtp related ---

    public static ConfigurationPathKey offlinePaymentDays(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.OFFLINE_PAYMENT_DAYS);
    }

    public static ConfigurationPathKey offlineReminderHours() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.OFFLINE_REMINDER_HOURS);
    }

    public static ConfigurationPathKey bankAccountNr(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.BANK_ACCOUNT_NR);
    }

    public static ConfigurationPathKey partialReservationIdLength() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.PARTIAL_RESERVATION_ID_LENGTH);
    }

    // --- mailgun related ---
    public static ConfigurationPathKey mailgunKey(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAILGUN_KEY);
    }

    public static ConfigurationPathKey mailgunDomain(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAILGUN_DOMAIN);
    }

    public static ConfigurationPathKey mailgunFrom(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.MAILGUN_FROM);
    }
    // --- end mailgun related ---

    public static ConfigurationPathKey googleAnalyticsKey() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.GOOGLE_ANALYTICS_KEY);
    }

    public static ConfigurationPathKey googleAnalyticsAnonymousMode() {
        return new ConfigurationPathKey(system(), ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE);
    }

    public static ConfigurationPathKey allowFreeTicketsCancellation(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION);
    }

    public static ConfigurationPathKey enableWaitingQueue(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ENABLE_WAITING_QUEUE);
    }

    public static ConfigurationPathKey enablePreRegistration(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ENABLE_PRE_REGISTRATION);
    }

    public static ConfigurationPathKey notifyForEachWaitingQueueSubscription(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.ENABLE_WAITING_QUEUE_NOTIFICATION);
    }

    public static ConfigurationPathKey waitingQueueReservationTimeout(Event event) {
        return new ConfigurationPathKey(event(event), ConfigurationKeys.WAITING_QUEUE_RESERVATION_TIMEOUT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Configuration that = (Configuration) o;

        return new EqualsBuilder()
            .append(key, that.key)
            .append(configurationKey, that.configurationKey)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
            .append(key)
            .append(configurationKey)
            .toHashCode();
    }
}
