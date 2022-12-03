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

import alfio.controller.support.Formatters;
import alfio.model.PriceContainer;
import alfio.model.PurchaseContext;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class SubscriptionDescriptorWithAdditionalInfo implements ApiPurchaseContext {
    private final SubscriptionDescriptor subscriptionDescriptor;
    private final InvoicingConfiguration invoicingConfiguration;
    private final AnalyticsConfiguration analyticsConfiguration;
    private final EventWithAdditionalInfo.CaptchaConfiguration captchaConfiguration;
    private final EmbeddingConfiguration embeddingConfiguration;

    //payment related information
    private final String bankAccount;
    private final List<String> bankAccountOwner;
    //

    private final String organizationEmail;
    private final String organizationName;

    private final DatesWithTimeZoneOffset salePeriod;
    private final Map<String, String> formattedOnSaleFrom;
    private final Map<String, String> formattedOnSaleTo;
    private final String timeZone;
    private final Map<String, String> formattedValidFrom;
    private final Map<String, String> formattedValidTo;
    private final Integer numAvailable;

    @Override
    public InvoicingConfiguration getInvoicingConfiguration() {
        return invoicingConfiguration;
    }

    @Override
    public EventWithAdditionalInfo.AssignmentConfiguration getAssignmentConfiguration() {
        return new EventWithAdditionalInfo.AssignmentConfiguration(false, false, false);
    }

    @Override
    public AnalyticsConfiguration getAnalyticsConfiguration() {
        return analyticsConfiguration;
    }

    @Override
    public boolean isFree() {
        return subscriptionDescriptor.isFreeOfCharge();
    }

    @Override
    public String getCurrency() {
        return subscriptionDescriptor.getCurrency();
    }

    public String getVat() {
        return subscriptionDescriptor.getVat().toString();
    }

    @Override
    @JsonIgnore
    public PurchaseContext purchaseContext() {
        return subscriptionDescriptor;
    }

    @Override
    public EventWithAdditionalInfo.CaptchaConfiguration getCaptchaConfiguration() {
        return captchaConfiguration;
    }

    @Override
    public boolean isVatIncluded() {
        return subscriptionDescriptor.getVatStatus() == PriceContainer.VatStatus.INCLUDED;
    }

    @Override
    public String getPrivacyPolicyUrl() {
        return purchaseContext().getPrivacyPolicyLinkOrNull();
    }

    @Override
    public String getTermsAndConditionsUrl() {
        return purchaseContext().getTermsAndConditionsUrl();
    }

    @Override
    public String getFileBlobId() {
        return subscriptionDescriptor.getFileBlobId();
    }

    @Override
    public Map<String, String> getDescription() {
        return Formatters.applyCommonMark(subscriptionDescriptor.getDescription());
    }

    public Map<String, String> getTitle() {
        return subscriptionDescriptor.getTitle();
    }

    @Override
    public String getBankAccount() {
        return bankAccount;
    }

    @Override
    public List<String> getBankAccountOwner() {
        return bankAccountOwner;
    }

    @Override
    public String getOrganizationEmail() {
        return organizationEmail;
    }

    @Override
    public String getOrganizationName() {
        return organizationName;
    }

    public String getFormattedPrice() {
        return MonetaryUtil.formatCents(subscriptionDescriptor.getPrice(), subscriptionDescriptor.getCurrency());
    }

    public DatesWithTimeZoneOffset getSalePeriod() {
        return salePeriod;
    }

    public Map<String, String> getFormattedOnSaleFrom() {
        return formattedOnSaleFrom;
    }

    public Map<String, String> getFormattedOnSaleTo() {
        return formattedOnSaleTo;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public SubscriptionDescriptor.SubscriptionValidityType getValidityType() {
        return subscriptionDescriptor.getValidityType();
    }

    public SubscriptionDescriptor.SubscriptionUsageType getUsageType() {
        return subscriptionDescriptor.getUsageType();
    }

    public SubscriptionDescriptor.SubscriptionTimeUnit getValidityTimeUnit() {
        return subscriptionDescriptor.getValidityTimeUnit();
    }

    public Integer getValidityUnits() {
        return subscriptionDescriptor.getValidityUnits();
    }

    public Map<String, String> getFormattedValidFrom() {
        return formattedValidFrom;
    }

    public Map<String, String> getFormattedValidTo() {
        return formattedValidTo;
    }

    public Integer getMaxEntries() {
        return subscriptionDescriptor.getMaxEntries() > 0 ? subscriptionDescriptor.getMaxEntries() : null;
    }

    @Override
    public OfflinePaymentConfiguration getOfflinePaymentConfiguration() {
        // offline payment is not supported for subscriptions
        return null;
    }

    @Override
    public EmbeddingConfiguration getEmbeddingConfiguration() {
        return embeddingConfiguration;
    }

    @Override
    public boolean isCanApplySubscriptions() {
        return false;//cannot buy a subscription with another subscription
    }

    public Integer getNumAvailable() {
        return numAvailable;
    }
}
