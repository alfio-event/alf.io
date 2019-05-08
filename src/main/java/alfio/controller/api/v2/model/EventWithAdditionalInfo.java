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

import alfio.model.Event;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
public class EventWithAdditionalInfo {
    private final Event event;
    private final String mapUrl;
    private final Organization organization;
    private final Map<String, String> description;
    private List<PaymentProxy> activePaymentMethods;


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

    public List<PaymentProxy> getActivePaymentMethods() {
        return activePaymentMethods;
    }
}
