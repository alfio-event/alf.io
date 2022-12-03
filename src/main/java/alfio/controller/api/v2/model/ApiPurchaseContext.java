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
import alfio.model.PurchaseContext;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.joda.money.CurrencyUnit;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ApiPurchaseContext {

    InvoicingConfiguration getInvoicingConfiguration();
    EventWithAdditionalInfo.AssignmentConfiguration getAssignmentConfiguration();
    AnalyticsConfiguration getAnalyticsConfiguration();

    OfflinePaymentConfiguration getOfflinePaymentConfiguration();
    EventWithAdditionalInfo.CaptchaConfiguration getCaptchaConfiguration();

    EmbeddingConfiguration getEmbeddingConfiguration();

    boolean isVatIncluded();
    boolean isFree();
    String getCurrency();
    String getVat();

    default List<Language> getContentLanguages() {
        return purchaseContext().getContentLanguages()
            .stream()
            .map(cl -> new Language(cl.getLocale().getLanguage(), cl.getDisplayLanguage()))
            .collect(Collectors.toList());
    }

    default CurrencyDescriptor getCurrencyDescriptor() {
        if(purchaseContext().isFreeOfCharge()) {
            return null;
        }
        var currencyUnit = CurrencyUnit.of(getCurrency());
        return new CurrencyDescriptor(currencyUnit.getCode(), currencyUnit.toCurrency().getDisplayName(), currencyUnit.getSymbol(), currencyUnit.getDecimalPlaces());
    }

    String getPrivacyPolicyUrl();

    String getTermsAndConditionsUrl();

    String getFileBlobId();

    Map<String, String> getTitle();
    Map<String, String> getDescription();

    String getBankAccount();
    List<String> getBankAccountOwner();


    String getOrganizationEmail();
    String getOrganizationName();

    @JsonIgnore
    PurchaseContext purchaseContext();

    boolean isCanApplySubscriptions();
}
