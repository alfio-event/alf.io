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

    INIT_COMPLETED("init succeeded", true),
    
    BASE_URL("Base application url", false),
    VAT_NR("VAT number", false),
    
    MAPS_SERVER_API_KEY("Google maps' server api key", false),
    MAPS_CLIENT_API_KEY("Google maps' client api key", false),
    
    STRIPE_SECRET_KEY("Stripe's secret key", false),
    STRIPE_PUBLIC_KEY("Stripe's public key", false),
    
    SPECIAL_PRICE_CODE_LENGTH("Length of special price code", false),
    MAX_AMOUNT_OF_TICKETS_BY_RESERVATION("Max amount of tickets", false),

    //smtp configuration related keys
    SMTP_HOST("SMTP hostname", false),
    SMTP_PORT("SMTP port", false),
    SMTP_PROTOCOL("SMTP Protocol (smtp or smtps)", false), //smtp or smtps
    SMTP_USERNAME("SMTP Username", false),
    SMTP_PASSWORD("SMTP Password", false),
    SMTP_FROM_EMAIL("E-Mail sender", false),
    SMTP_PROPERTIES("SMTP Properties", false),
    GOOGLE_ANALYTICS_KEY("Google Analytics tracking ID", false);

    private static final Predicate<ConfigurationKeys> INTERNAL = ConfigurationKeys::isInternal;
    private final String description;
    private final boolean internal;

    private ConfigurationKeys(String description, boolean internal) {
        this.description = description;
        this.internal = internal;
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
