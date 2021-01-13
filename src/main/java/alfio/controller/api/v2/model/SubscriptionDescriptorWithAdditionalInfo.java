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

import alfio.model.PriceContainer;
import alfio.model.PurchaseContext;
import alfio.model.subscription.SubscriptionDescriptor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
public class SubscriptionDescriptorWithAdditionalInfo implements ApiPurchaseContext {
    private final SubscriptionDescriptor subscriptionDescriptor;
    private final EventWithAdditionalInfo.InvoicingConfiguration invoicingConfiguration;
    private final AnalyticsConfiguration analyticsConfiguration;
    private final EventWithAdditionalInfo.CaptchaConfiguration captchaConfiguration;

    //payment related information
    private final String bankAccount;
    private final List<String> bankAccountOwner;
    //

    @Override
    public EventWithAdditionalInfo.InvoicingConfiguration getInvoicingConfiguration() {
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
        return subscriptionDescriptor.getDescription();
    }

    @Override
    public String getBankAccount() {
        return bankAccount;
    }

    @Override
    public List<String> getBankAccountOwner() {
        return bankAccountOwner;
    }
}
