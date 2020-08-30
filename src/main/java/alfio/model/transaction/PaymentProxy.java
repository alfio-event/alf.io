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

    // TODO: remove this enum and move all his properties to the corresponding PaymentProvider implementations

    STRIPE("stripe.com", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_STRIPE), true, Collections.emptySet(), PaymentMethod.CREDIT_CARD),
    ON_SITE("on-site payment", true, true, Collections.emptySet(), false, Collections.emptySet(), PaymentMethod.ON_SITE),
    OFFLINE("offline payment", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_OFFLINE), false, Collections.emptySet(), PaymentMethod.BANK_TRANSFER),
    NONE("no payment required", false, false, Collections.emptySet(), false, Collections.emptySet(), PaymentMethod.NONE),
    ADMIN("manual", false, false, Collections.emptySet(), false, Collections.emptySet(), PaymentMethod.NONE),
    PAYPAL("paypal", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_PAYPAL), true, Collections.emptySet(), PaymentMethod.PAYPAL),
    MOLLIE("mollie", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_MOLLIE), true, Collections.emptySet(), PaymentMethod.IDEAL),
    SAFERPAY("saferpay", false, true, EnumSet.of(ConfigurationKeys.SettingCategory.PAYMENT_SAFERPAY), false, Collections.emptySet(), PaymentMethod.CREDIT_CARD);

    private final String description;
    private final boolean deskPayment;
    private final boolean visible;
    private final Set<ConfigurationKeys.SettingCategory> settingCategories;
    private final boolean supportRefund;
    private final Set<String> onlyForCurrency;
    private final PaymentMethod paymentMethod;

    PaymentProxy(String description, boolean deskPayment, boolean visible, Set<ConfigurationKeys.SettingCategory> settingCategories, boolean supportRefund, Set<String> onlyForCurrency, PaymentMethod paymentMethod) {
        this.description = description;
        this.deskPayment = deskPayment;
        this.visible = visible;
        this.settingCategories = settingCategories;
        this.supportRefund = supportRefund;
        this.onlyForCurrency = onlyForCurrency;
        this.paymentMethod = paymentMethod;
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

    public static PaymentProxy fromPaymentMethod(PaymentMethod paymentMethod) {
        return availableProxies().stream().filter(pp -> pp.getPaymentMethod() == paymentMethod).findFirst().orElse(null);
    }

    public Set<String> getOnlyForCurrency() {
        return onlyForCurrency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
}
