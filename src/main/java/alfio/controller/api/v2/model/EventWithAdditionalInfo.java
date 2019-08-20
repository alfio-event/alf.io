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
package alfio.controller.api.v2.model;

import alfio.controller.api.support.CurrencyDescriptor;
import alfio.model.Event;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.joda.money.CurrencyUnit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class EventWithAdditionalInfo implements DateValidity {
    private final Event event;
    private final String mapUrl;
    private final Organization.OrganizationContact organization;
    private final Map<String, String> description;
    private final Map<PaymentMethod, PaymentProxyWithParameters> activePaymentMethods;

    //payment related informations
    private final String bankAccount;
    private final List<String> bankAccountOwner;
    //

    //date related informations
    private final Map<String, String> formattedBeginDate; // day, month, year
    private final Map<String, String> formattedBeginTime; //the hour/minute component
    private final Map<String, String> formattedEndDate;
    private final Map<String, String> formattedEndTime;
    //

    //
    private final InvoicingConfiguration invoicingConfiguration;
    //

    //
    private final CaptchaConfiguration captchaConfiguration;
    //

    //
    private final AssignmentConfiguration assignmentConfiguration;
    //

    //
    private final PromotionsConfiguration promotionsConfiguration;
    //

    private final AnalyticsConfiguration analyticsConfiguration;

    private final Map<String, Map<String, String>> i18nOverride;

    public String getShortName() {
        return event.getShortName();
    }

    public String getDisplayName() {
        return event.getDisplayName();
    }

    public String getFileBlobId() {
        return event.getFileBlobId();
    }

    public String getWebsiteUrl() {
        return event.getWebsiteUrl();
    }

    public CurrencyDescriptor getCurrencyDescriptor() {
        if(event.isFreeOfCharge()) {
            return null;
        }
        var currencyUnit = CurrencyUnit.of(event.getCurrency());
        return new CurrencyDescriptor(currencyUnit.getCode(), currencyUnit.toCurrency().getDisplayName(), currencyUnit.getSymbol(), currencyUnit.getDecimalPlaces());
    }

    public List<Language> getContentLanguages() {
        return event.getContentLanguages()
            .stream()
            .map(cl -> new Language(cl.getLocale().getLanguage(), cl.getDisplayLanguage()))
            .collect(Collectors.toList());
    }

    public String getMapUrl() {
        return mapUrl;
    }

    public String getOrganizationName() {
        return organization.getName();
    }

    public String getOrganizationEmail() {
        return organization.getEmail();
    }

    public String getLocation() {
        return event.getLocation();
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public String getPrivacyPolicyUrl() {
        return event.getPrivacyPolicyLinkOrNull();
    }

    public String getTermsAndConditionsUrl() {
        return event.getTermsAndConditionsUrl();
    }

    public String getCurrency() {
        return event.getCurrency();
    }

    public boolean isVatIncluded() {
        return event.isVatIncluded();
    }

    public String getVat() {
        return event.getVat().toString();
    }

    public boolean isFree() {
        return event.getFree();
    }

    public Map<PaymentMethod, PaymentProxyWithParameters> getActivePaymentMethods() {
        return activePaymentMethods;
    }

    public String getBankAccount() {
        return bankAccount;
    }

    public List<String> getBankAccountOwner() {
        return bankAccountOwner;
    }


    // date related fields
    public boolean isSameDay() {
        return event.getSameDay();
    }

    public Map<String, String> getFormattedBeginDate() {
        return formattedBeginDate;
    }

    public Map<String, String> getFormattedBeginTime() {
        return formattedBeginTime;
    }

    public Map<String, String> getFormattedEndDate() {
        return formattedEndDate;
    }

    public Map<String, String> getFormattedEndTime() {
        return formattedEndTime;
    }

    public String getTimeZone() {
        return event.getTimeZone();
    }
    //


    public InvoicingConfiguration getInvoicingConfiguration() {
        return invoicingConfiguration;
    }

    public CaptchaConfiguration getCaptchaConfiguration() {
        return captchaConfiguration;
    }

    public AssignmentConfiguration getAssignmentConfiguration() {
        return assignmentConfiguration;
    }

    public PromotionsConfiguration getPromotionsConfiguration() {
        return promotionsConfiguration;
    }

    public AnalyticsConfiguration getAnalyticsConfiguration() {
        return analyticsConfiguration;
    }

    public Map<String, Map<String, String>> getI18nOverride() {
        return i18nOverride;
    }

    @AllArgsConstructor
    @Getter
    public static class PaymentProxyWithParameters {
        private final PaymentProxy paymentProxy;
        private final Map<String, ?> parameters;
    }


    @AllArgsConstructor
    @Getter
    public static class InvoicingConfiguration {
        private final boolean userCanDownloadReceiptOrInvoice;
        private final boolean euVatCheckingEnabled;
        private final boolean invoiceAllowed;
        private final boolean onlyInvoice;
        private final boolean customerReferenceEnabled;
        private final boolean enabledItalyEInvoicing;
        private final boolean vatNumberStrictlyRequired;
    }

    @AllArgsConstructor
    @Getter
    public static class CaptchaConfiguration {
        private final boolean captchaForTicketSelection;
        private final boolean captchaForOfflinePaymentAndFree;
        private final String recaptchaApiKey;
    }

    @AllArgsConstructor
    @Getter
    public static class AssignmentConfiguration {
        private final boolean forceAssignment;
        private final boolean enableAttendeeAutocomplete;
        private final boolean enableTicketTransfer;
    }

    @AllArgsConstructor
    @Getter
    public static class PromotionsConfiguration {
        private final boolean hasAccessPromotions;
        private final boolean usePartnerCode;
    }

}
