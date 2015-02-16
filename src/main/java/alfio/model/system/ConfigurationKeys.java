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

import java.util.Arrays;
import java.util.function.Predicate;

@Getter
public enum ConfigurationKeys {

    INIT_COMPLETED("init succeeded", true, Type.GENERAL),
    
    BASE_URL("Base application url", false, Type.GENERAL),
    VAT_NR("VAT number", false, Type.GENERAL),
    
    MAPS_SERVER_API_KEY("Google maps' server api key", false, Type.GENERAL),
    MAPS_CLIENT_API_KEY("Google maps' client api key", false, Type.GENERAL),
    
    STRIPE_SECRET_KEY("Stripe's secret key", false, Type.PAYMENT),
    STRIPE_PUBLIC_KEY("Stripe's public key", false, Type.PAYMENT),
    
    SPECIAL_PRICE_CODE_LENGTH("Length of special price code", false, Type.GENERAL),
    MAX_AMOUNT_OF_TICKETS_BY_RESERVATION("Max amount of tickets", false, Type.GENERAL),
    
    //
    MAILER_TYPE("Mailer type (if not set, default will be smtp)", false, Type.MAIL),//valid values: smtp | mailgun
    //

    MAX_EMAIL_PER_CYCLE("How many e-mail should be managed within 5 sec.", false, Type.MAIL),

    MAIL_REPLY_TO("Reply-to address", false, Type.MAIL),

    //smtp configuration related keys
    SMTP_HOST("SMTP hostname", false, Type.MAIL),
    SMTP_PORT("SMTP port", false, Type.MAIL),
    SMTP_PROTOCOL("SMTP Protocol (smtp or smtps)", false, Type.MAIL), //smtp or smtps
    SMTP_USERNAME("SMTP Username", false, Type.MAIL),
    SMTP_PASSWORD("SMTP Password", false, Type.MAIL),
    SMTP_FROM_EMAIL("E-Mail sender", false, Type.MAIL),
    SMTP_PROPERTIES("SMTP Properties", false, Type.MAIL),

    OFFLINE_PAYMENT_DAYS("Maximum number of days allowed to pay an offline ticket", false, Type.PAYMENT),
    OFFLINE_REMINDER_HOURS("How many hours before expiration should be sent a reminder e-mail for offline payments?", false, Type.PAYMENT),
    BANK_ACCOUNT_NR("Bank Account number", false, Type.PAYMENT),
    PARTIAL_RESERVATION_ID_LENGTH("Partial reservationID length", false, Type.PAYMENT),
    //
    
    //mailgun configuration related info
    MAILGUN_KEY("Mailgun key", false, Type.MAIL),
    MAILGUN_DOMAIN("Mailgun domain", false, Type.MAIL),
    MAILGUN_FROM("Mailgun E-Mail sender", false, Type.MAIL),
    //
    
    GOOGLE_ANALYTICS_KEY("Google Analytics tracking ID", false, Type.GENERAL);

    @Getter
    public enum Type {
        GENERAL("General settings"),
        PAYMENT("Payment provider settings"),
        MAIL("E-Mail settings");

        private final String description;
        Type(String description) {
            this.description = description;
        }

    }

    private static final Predicate<ConfigurationKeys> INTERNAL = ConfigurationKeys::isInternal;
    private final String description;
    private final boolean internal;
    private final Type type;

    private ConfigurationKeys(String description, boolean internal, Type type) {
        this.description = description;
        this.internal = internal;
        this.type = type;
    }

    public String getValue() {
        return name();
    }

    public static ConfigurationKeys fromValue(String value) {
        return valueOf(value);
    }

    public static ConfigurationKeys[] visible() {
        return Arrays.stream(values())
                .filter(INTERNAL.negate())
                .toArray(ConfigurationKeys[]::new);
    }

}
