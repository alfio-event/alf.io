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

import lombok.Getter;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationPathLevel.*;
import static java.util.stream.Collectors.toList;

@Getter
public enum ConfigurationKeys {

    INIT_COMPLETED("init succeeded", true, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.noneOf(ConfigurationPathLevel.class), true),
    SUPPORTED_LANGUAGES("supported languages", false, SettingCategory.GENERAL, ComponentType.LIST, true, EnumSet.of(SYSTEM), true),

    BASE_URL("Base application url", false, SettingCategory.GENERAL, ComponentType.TEXT, true, EnumSet.of(SYSTEM), true),

    MAPS_PROVIDER("Select the maps provider (Google, Here)", false, SettingCategory.MAP, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAPS_CLIENT_API_KEY("Google maps' client api key", false, SettingCategory.MAP, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAPS_HERE_APP_ID("HERE map App ID", false, SettingCategory.MAP, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAPS_HERE_APP_CODE("HERE map App Code", false, SettingCategory.MAP, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),


    RECAPTCHA_API_KEY("Recaptcha api key", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    RECAPTCHA_SECRET("Recaptcha secret", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS("Enable captcha for offline payments / free of charge tickets (default false)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM), true),
    ENABLE_CAPTCHA_FOR_LOGIN("Enable captcha for login (default true)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM), true),

    DISPLAY_STATS_IN_EVENT_DETAIL("Display stats (sold tickets, gross income, pending reservations) in event detail (default true)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    DEMO_MODE_ACCOUNT_EXPIRATION_DAYS("Account expiration days", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    PLATFORM_MODE_ENABLED("Enable Platform mode", false, SettingCategory.PAYMENT, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM), true),
    PLATFORM_FEE("Platform fee to apply for each ticket sold (if you want to apply a percentage, just add '%' at the end). ", false, SettingCategory.PAYMENT, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    PLATFORM_MINIMUM_FEE("Platform minimum fee to apply to free tickets", false, SettingCategory.PAYMENT, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    PAYMENT_METHODS_BLACKLIST("Payment methods blacklist. Comma-separated list of methods", false, SettingCategory.PAYMENT, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),

    STRIPE_SECRET_KEY("Stripe's secret key", false, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    STRIPE_PUBLIC_KEY("Stripe's public key", false, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    STRIPE_CONNECT_CLIENT_ID("Stripe Connect Client ID", false, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    STRIPE_CONNECT_CALLBACK("Stripe Connect Callback URL", false, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    STRIPE_WEBHOOK_KEY("Stripe Connect WebHook signature key", false, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    STRIPE_CONNECTED_ID("Connected ID", true, SettingCategory.PAYMENT_STRIPE, ComponentType.TEXT, false, EnumSet.noneOf(ConfigurationPathLevel.class), true),

    SPECIAL_PRICE_CODE_LENGTH("Length of special price code", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAX_AMOUNT_OF_TICKETS_BY_RESERVATION("How many tickets can be purchased in a single reservation (default 5)", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT, TICKET_CATEGORY), true),
    ASSIGNMENT_REMINDER_START("How many days before the event should be sent a reminder to the users about Tickets assignment? (default: 10 days)", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ASSIGNMENT_REMINDER_INTERVAL("How long should be the 'quiet period' (in days) between the reminders? (default: 3 days)", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    OPTIONAL_DATA_REMINDER_ENABLED("Send a reminder for optional data? (default: true)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    RESERVATION_TIMEOUT("The amount of time, in MINUTES, that the user have to complete the reservation process (default: 25 min)", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION("Force ticket owner assignment at reservation time", false, SettingCategory.RESERVATION_UI, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ENABLE_CUSTOMER_REFERENCE("Enable Customer Reference (Purchase Order) field in contact detail (default: false)", false, SettingCategory.RESERVATION_UI, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ENABLE_ATTENDEE_AUTOCOMPLETE("Enable attendee autocomplete for 1-ticket reservations (default: true)", false, SettingCategory.RESERVATION_UI, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    //
    MAILER_TYPE("Mailer type (if not set, default will be disabled)", false, SettingCategory.MAIL, ComponentType.TEXT, true, EnumSet.of(SYSTEM), true),//valid values: smtp | mailgun
    //

    MAX_EMAIL_PER_CYCLE("How many e-mail should be managed within 5 sec.", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),

    MAIL_REPLY_TO("Reply-to address", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    MAIL_SYSTEM_NOTIFICATION_CC("Add additional CC when the system send notifications to the event organizer, can insert multiple email (comma separated)", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    //smtp configuration related keys
    SMTP_HOST("SMTP hostname", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),
    SMTP_PORT("SMTP port", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),
    SMTP_PROTOCOL("SMTP Protocol (smtp or smtps)", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false), //smtp or smtps
    SMTP_USERNAME("SMTP Username", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),
    SMTP_PASSWORD("SMTP Password", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),
    SMTP_FROM_EMAIL("E-Mail sender", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),
    SMTP_PROPERTIES("SMTP Properties", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),

    OFFLINE_PAYMENT_DAYS("Maximum number of days allowed to pay an offline ticket", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    OFFLINE_REMINDER_HOURS("How many hours before expiration should be sent a reminder e-mail for offline payments?", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    BANK_ACCOUNT_NR("Bank Account number", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    BANK_ACCOUNT_OWNER("Bank Account owner", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.TEXTAREA, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    AUTOMATIC_REMOVAL_EXPIRED_OFFLINE_PAYMENT("Cancel Reservation automatically when payment is overdue", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    PARTIAL_RESERVATION_ID_LENGTH("Partial reservationID length", false, SettingCategory.PAYMENT_OFFLINE, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    //

    //mailgun configuration related info
    MAILGUN_KEY("Mailgun key", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAILGUN_DOMAIN("Mailgun domain", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAILGUN_FROM("Mailgun E-Mail sender", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    //
    // mailjet
    MAILJET_APIKEY_PUBLIC("Mailjet public api key", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAILJET_APIKEY_PRIVATE("Mailjet private api key", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    MAILJET_FROM("Mailjet E-Mail sender", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    //

    GOOGLE_ANALYTICS_KEY("Google Analytics tracking ID", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    GOOGLE_ANALYTICS_ANONYMOUS_MODE("Run Google Analytics without cookies and scrambling the client IP address (default true)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM), true),

    ALLOW_FREE_TICKETS_CANCELLATION("Allow cancellation for free tickets", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT, TICKET_CATEGORY), true),

    ENABLE_WAITING_QUEUE("Enable waiting queue in case of sold-out (default: false)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ENABLE_PRE_REGISTRATION("Enable pre-registration (default false)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ENABLE_WAITING_QUEUE_NOTIFICATION("Do you want to receive an e-mail when someone subscribes to the waiting queue? (default: false)", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    WAITING_QUEUE_RESERVATION_TIMEOUT("The maximum time, in hours, before the \"waiting queue\" reservation would expire (default: 4)", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    STOP_WAITING_QUEUE_SUBSCRIPTIONS("Stop subscriptions", true, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.noneOf(ConfigurationPathLevel.class), true),

    //
    MAIL_ATTEMPTS_COUNT("The number of attempts when trying to sending an email (default: 10)", false, SettingCategory.MAIL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    //
    PAYPAL_CLIENT_ID("Paypal REST API client ID", false, SettingCategory.PAYMENT_PAYPAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    PAYPAL_CLIENT_SECRET("Paypal REST API client secret", false, SettingCategory.PAYMENT_PAYPAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    PAYPAL_LIVE_MODE("Enable live mode for Paypal", false, SettingCategory.PAYMENT_PAYPAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION), true),
    PAYPAL_DEMO_MODE_USERNAME("Paypal demo mode username", false, SettingCategory.PAYMENT_PAYPAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    PAYPAL_DEMO_MODE_PASSWORD("Paypal demo mode password", false, SettingCategory.PAYMENT_PAYPAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM), true),
    //

    //
    MOLLIE_API_KEY("Mollie API key", false, SettingCategory.PAYMENT_MOLLIE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    //

    //
    VAT_NR("VAT number", false, SettingCategory.INVOICE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION), true),
    INVOICE_NUMBER_PATTERN("Invoice number pattern, example: INVOICE-%d", false, SettingCategory.INVOICE, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    INVOICE_ADDRESS("Invoice address", false, SettingCategory.INVOICE, ComponentType.TEXTAREA, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    GENERATE_ONLY_INVOICE("Generate only invoice", false, SettingCategory.INVOICE, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),
    ENABLE_EU_VAT_DIRECTIVE("Enable EU VAT handling for EU Companies", false, SettingCategory.INVOICE_EU, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    APPLY_VAT_FOREIGN_BUSINESS("Apply VAT for non-EU B2B customers (default true)", false, SettingCategory.INVOICE_EU, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION), true),
    COUNTRY_OF_BUSINESS("The Country where the organizer runs its Business (can differ from event location)", false, SettingCategory.INVOICE_EU, ComponentType.LIST, false, EnumSet.of(SYSTEM, ORGANIZATION), false),
    EU_COUNTRIES_LIST("EU Countries", true, SettingCategory.INVOICE_EU, ComponentType.LIST, false, EnumSet.of(SYSTEM), false),
    EU_VAT_API_ADDRESS("EU VAT API address", false, SettingCategory.INVOICE_EU, ComponentType.TEXT, false, EnumSet.of(SYSTEM), false),

    //

    //PASSBOOK
    PASSBOOK_TYPE_IDENTIFIER("Passbook type identifier", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    PASSBOOK_TEAM_IDENTIFIER("Passbook team identifier", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    PASSBOOK_KEYSTORE("Passbook keystore(base64 encoded keystore)", false, SettingCategory.GENERAL, ComponentType.TEXTAREA, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    PASSBOOK_KEYSTORE_PASSWORD("Passbook keystore password", false, SettingCategory.GENERAL, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),

    //CHECK-IN
    CHECK_IN_STATS("Display check-in statistics in mobile apps", false, SettingCategory.GENERAL, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), true),

    //ALF.IO-PI
    ALFIO_PI_INTEGRATION_ENABLED("Enable Alf.io-PI integration", false, SettingCategory.ALFIO_PI, ComponentType.BOOLEAN, false, EnumSet.of(SYSTEM), false),
    OFFLINE_CHECKIN_ENABLED("Offline Check-in enabled", false, SettingCategory.ALFIO_PI, ComponentType.BOOLEAN, false, EnumSet.of(EVENT), false),
    LABEL_PRINTING_ENABLED("Label Printing enabled", false, SettingCategory.ALFIO_PI, ComponentType.BOOLEAN, false, EnumSet.of(EVENT), false),
    LABEL_LAYOUT("Label layout", false, SettingCategory.ALFIO_PI, ComponentType.TEXTAREA, false, EnumSet.of(EVENT), false),

    //
    TRANSLATION_OVERRIDE_VAT_EN("Override the default tax term EN: VAT", false, SettingCategory.TRANSLATIONS, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    TRANSLATION_OVERRIDE_VAT_DE("Override the default tax term DE: Mehrwertsteuer", false, SettingCategory.TRANSLATIONS, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    TRANSLATION_OVERRIDE_VAT_FR("Override the default tax term FR: TVA", false, SettingCategory.TRANSLATIONS, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    TRANSLATION_OVERRIDE_VAT_IT("Override the default tax term IT: IVA", false, SettingCategory.TRANSLATIONS, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false),
    TRANSLATION_OVERRIDE_VAT_NL("Override the default tax term NL: BTW", false, SettingCategory.TRANSLATIONS, ComponentType.TEXT, false, EnumSet.of(SYSTEM, ORGANIZATION, EVENT), false);

    @Getter
    public enum SettingCategory {
        GENERAL("General settings"),
        RESERVATION_UI("Reservation Process UI"),
        PAYMENT("Payment"),
        PAYMENT_STRIPE("Stripe.com settings"),
        PAYMENT_PAYPAL("PayPal settings"),
        PAYMENT_OFFLINE("Offline payment settings"),
        PAYMENT_MOLLIE("Mollie settings"),
        INVOICE("Invoice settings"),
        INVOICE_EU("Invoice settings for EU"),
        MAIL("E-Mail settings"),
        ALFIO_PI("Offline check-in and badge printing"),
        MAP("Maps settings"),
        TRANSLATIONS("Translations");

        private final String description;
        SettingCategory(String description) {
            this.description = description;
        }

    }

    public enum GeoInfoProvider {
        GOOGLE, HERE, NONE
    }

    private static final Predicate<ConfigurationKeys> INTERNAL = ConfigurationKeys::isInternal;
    private final String description;
    private final boolean internal;
    private final SettingCategory category;
    private final ComponentType componentType;
    private final boolean basic;
    private final Collection<ConfigurationPathLevel> pathLevels;
    private final boolean backedByDefault;

    ConfigurationKeys(String description, boolean internal, SettingCategory category, ComponentType componentType, boolean basic, Collection<ConfigurationPathLevel> pathLevels, boolean backedByDefault) {
        this.description = description;
        this.internal = internal;
        this.category = category;
        this.componentType = componentType;
        this.basic = basic;
        this.pathLevels = pathLevels;
        this.backedByDefault = backedByDefault;
    }

    public String getValue() {
        return name();
    }

    public boolean supports(ConfigurationPathLevel pathLevel) {
        return pathLevels.contains(pathLevel);
    }

    public static ConfigurationKeys fromString(String configurationKey) {
        return valueOf(configurationKey);
    }

    public static ConfigurationKeys[] visible() {
        return visibleStream().toArray(ConfigurationKeys[]::new);
    }

    private static Stream<ConfigurationKeys> visibleStream() {
        return Arrays.stream(values()).filter(INTERNAL.negate());
    }

    public static List<ConfigurationKeys> basic() {
        return visibleStream()
            .filter(ConfigurationKeys::isBasic)
            .collect(toList());

    }

    public boolean isBooleanComponentType() {
        return componentType == ComponentType.BOOLEAN;
    }

    public static List<ConfigurationKeys> byPathLevel(ConfigurationPathLevel pathLevel) {
        return visibleStream().filter(k -> k.supports(pathLevel)).collect(toList());
    }

    public static List<ConfigurationKeys> byCategory(Set<SettingCategory> categorySet) {
        return visibleStream().filter(k -> categorySet.contains(k.category)).collect(toList());
    }

}
