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
package alfio.controller.api.v2.user.support;

import alfio.controller.api.v2.model.EventWithAdditionalInfo;
import alfio.controller.api.v2.model.InvoicingConfiguration;
import alfio.manager.EuVatChecker;
import alfio.manager.system.ConfigurationManager;
import alfio.model.PurchaseContext;
import alfio.model.system.ConfigurationKeys;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Map;

import static alfio.model.system.ConfigurationKeys.*;

public class PurchaseContextInfoBuilder {

    private PurchaseContextInfoBuilder() {}

    public static Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationsValues(PurchaseContext purchaseContext, ConfigurationManager configurationManager) {
        return configurationManager.getFor(List.of(
            MAPS_PROVIDER,
            MAPS_CLIENT_API_KEY,
            MAPS_HERE_API_KEY,
            RECAPTCHA_API_KEY,
            BANK_ACCOUNT_NR,
            BANK_ACCOUNT_OWNER,
            ENABLE_CUSTOMER_REFERENCE,
            ENABLE_ITALY_E_INVOICING,
            VAT_NUMBER_IS_REQUIRED,
            FORCE_TICKET_OWNER_ASSIGNMENT_AT_RESERVATION,
            ENABLE_ATTENDEE_AUTOCOMPLETE,
            ENABLE_TICKET_TRANSFER,
            DISPLAY_DISCOUNT_CODE_BOX,
            USE_PARTNER_CODE_INSTEAD_OF_PROMOTIONAL,
            GOOGLE_ANALYTICS_KEY,
            GOOGLE_ANALYTICS_ANONYMOUS_MODE,
            // captcha
            ENABLE_CAPTCHA_FOR_TICKET_SELECTION,
            RECAPTCHA_API_KEY,
            ENABLE_CAPTCHA_FOR_OFFLINE_PAYMENTS,
            //
            GENERATE_ONLY_INVOICE,
            //
            INVOICE_ADDRESS,
            VAT_NR,
            SHOW_ONLY_BASIC_INSTRUCTIONS,
            // required by EuVatChecker.reverseChargeEnabled
            ENABLE_EU_VAT_DIRECTIVE,
            COUNTRY_OF_BUSINESS,
            ENABLE_REVERSE_CHARGE_IN_PERSON,
            ENABLE_REVERSE_CHARGE_ONLINE,

            DISPLAY_TICKETS_LEFT_INDICATOR,
            EVENT_CUSTOM_CSS,
            EMBED_POST_MESSAGE_ORIGIN
        ), purchaseContext.getConfigurationLevel());
    }

    public static InvoicingConfiguration invoicingInfo(ConfigurationManager configurationManager,
                                                       Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationsValues) {
        boolean canGenerateReceiptOrInvoiceToCustomer = configurationManager.canGenerateReceiptOrInvoiceToCustomer(configurationsValues);
        boolean euVatCheckingEnabled = EuVatChecker.reverseChargeEnabled(configurationsValues);
        boolean invoiceAllowed = configurationManager.hasAllConfigurationsForInvoice(configurationsValues);
        boolean onlyInvoice = invoiceAllowed && configurationManager.isInvoiceOnly(configurationsValues);
        boolean customerReferenceEnabled = configurationsValues.get(ENABLE_CUSTOMER_REFERENCE).getValueAsBooleanOrDefault();
        boolean enabledItalyEInvoicing = configurationsValues.get(ENABLE_ITALY_E_INVOICING).getValueAsBooleanOrDefault();
        boolean vatNumberStrictlyRequired = configurationsValues.get(VAT_NUMBER_IS_REQUIRED).getValueAsBooleanOrDefault();

        return new InvoicingConfiguration(canGenerateReceiptOrInvoiceToCustomer,
            euVatCheckingEnabled, invoiceAllowed, onlyInvoice,
            customerReferenceEnabled, enabledItalyEInvoicing, vatNumberStrictlyRequired);

    }

    public static EventWithAdditionalInfo.CaptchaConfiguration captchaConfiguration(ConfigurationManager configurationManager, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationsValues) {
        boolean captchaForTicketSelection = isRecaptchaForTicketSelectionEnabled(configurationsValues);
        String recaptchaApiKey = null;
        if (captchaForTicketSelection) {
            recaptchaApiKey = configurationsValues.get(RECAPTCHA_API_KEY).getValueOrNull();
        }
        //
        boolean captchaForOfflinePaymentAndFreeEnabled = configurationManager.isRecaptchaForOfflinePaymentAndFreeEnabled(configurationsValues);
        return new EventWithAdditionalInfo.CaptchaConfiguration(captchaForTicketSelection, captchaForOfflinePaymentAndFreeEnabled, recaptchaApiKey);
    }

    public static boolean isRecaptchaForTicketSelectionEnabled(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> configurationValues) {
        Validate.isTrue(configurationValues.containsKey(ENABLE_CAPTCHA_FOR_TICKET_SELECTION) && configurationValues.containsKey(RECAPTCHA_API_KEY));
        return configurationValues.get(ENABLE_CAPTCHA_FOR_TICKET_SELECTION).getValueAsBooleanOrDefault() &&
            configurationValues.get(RECAPTCHA_API_KEY).getValueOrNull() != null;
    }
}
