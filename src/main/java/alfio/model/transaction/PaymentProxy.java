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
package alfio.model.transaction;

import alfio.model.system.ConfigurationKeys;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public enum PaymentProxy {
    STRIPE("stripe.com", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_STRIPE), true),
    ON_SITE("on-site payment", true, true, Collections.emptySet(), false),
    OFFLINE("offline payment", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_OFFLINE), false),
    NONE("no payment required", false, false, Collections.emptySet(), false),
    ADMIN("manual", false, false, Collections.emptySet(), false),
    PAYPAL("paypal", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_PAYPAL), true),
    MOLLIE("mollie", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_MOLLIE), true);

    private final String description;
    private final boolean deskPayment;
    private final boolean visible;
    private final Set<ConfigurationKeys.SettingCategory> settingCategories;
    private final boolean supportRefund;

    PaymentProxy(String description, boolean deskPayment, boolean visible, Set<ConfigurationKeys.SettingCategory> settingCategories, boolean supportRefund) {
        this.description = description;
        this.deskPayment = deskPayment;
        this.visible = visible;
        this.settingCategories = settingCategories;
        this.supportRefund = supportRefund;
    }

    public String getDescription() {
        return description;
    }

    public String getKey() {
        return name();
    }

    public boolean isDeskPaymentRequired() {
        return deskPayment;
    }

    private boolean isVisible() {
        return visible;
    }

    public boolean isSupportRefund() {
        return supportRefund;
    }

    @JsonIgnore
    public Set<ConfigurationKeys.SettingCategory> getSettingCategories() {
        return settingCategories;
    }

    public static Optional<PaymentProxy> safeValueOf(String name) {
        return Arrays.stream(values()).filter(p -> StringUtils.equals(p.name(), name)).findFirst();
    }

    public static List<PaymentProxy> availableProxies() {
        return Arrays.stream(values()).filter(PaymentProxy::isVisible).collect(Collectors.toList());
    }

}
