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
package alfio.model.modification;

import alfio.model.PriceContainer.VatStatus;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionTimeUnit;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionValidityType;
import alfio.model.transaction.PaymentProxy;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class SubscriptionDescriptorModification {
    private final UUID id;
    private final Map<String, String> title;
    private final Map<String, String> description;
    private final Integer maxAvailable;
    private final ZonedDateTime onSaleFrom;
    private final ZonedDateTime onSaleTo;
    private final BigDecimal price;
    private final BigDecimal vat;
    private final VatStatus vatStatus;
    private final String currency;
    private final Boolean isPublic;
    private final int organizationId;

    private final Integer maxEntries;
    private final SubscriptionValidityType validityType;
    private final SubscriptionTimeUnit validityTimeUnit;
    private final Integer validityUnits;
    private final ZonedDateTime validityFrom;
    private final ZonedDateTime validityTo;
    private final SubscriptionUsageType usageType;

    private final String termsAndConditionsUrl;
    private final String privacyPolicyUrl;
    private final String fileBlobId;
    private final List<PaymentProxy> paymentProxies;
    private final ZoneId timeZone;
    private final Boolean supportsTicketsGeneration;

    public SubscriptionDescriptorModification(@JsonProperty("id") UUID id,
                                              @JsonProperty("title") Map<String, String> title,
                                              @JsonProperty("description") Map<String, String> description,
                                              @JsonProperty("maxAvailable") Integer maxAvailable,
                                              @JsonProperty("onSaleFrom") ZonedDateTime onSaleFrom,
                                              @JsonProperty("onSaleTo") ZonedDateTime onSaleTo,
                                              @JsonProperty("price") BigDecimal price,
                                              @JsonProperty("vat") BigDecimal vat,
                                              @JsonProperty("vatStatus") VatStatus vatStatus,
                                              @JsonProperty("currency") String currency,
                                              @JsonProperty("isPublic") Boolean isPublic,
                                              @JsonProperty("organizationId") int organizationId,
                                              @JsonProperty("maxEntries") Integer maxEntries,
                                              @JsonProperty("validityType") SubscriptionValidityType validityType,
                                              @JsonProperty("validityTimeUnit") SubscriptionTimeUnit validityTimeUnit,
                                              @JsonProperty("validityUnits") Integer validityUnits,
                                              @JsonProperty("validityFrom") ZonedDateTime validityFrom,
                                              @JsonProperty("validityTo") ZonedDateTime validityTo,
                                              @JsonProperty("usageType") SubscriptionUsageType usageType,
                                              @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                                              @JsonProperty("privacyPolicyUrl") String privacyPolicyUrl,
                                              @JsonProperty("fileBlobId") String fileBlobId,
                                              @JsonProperty("paymentProxies") List<PaymentProxy> paymentProxies,
                                              @JsonProperty("timeZone") ZoneId timeZone,
                                              @JsonProperty("supportsTicketsGeneration") Boolean supportsTicketsGeneration) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.maxAvailable = maxAvailable;
        this.onSaleFrom = onSaleFrom;
        this.onSaleTo = onSaleTo;
        this.price = price;
        this.vat = vat;
        this.vatStatus = vatStatus;
        this.currency = currency;
        this.isPublic = isPublic;
        this.organizationId = organizationId;
        this.maxEntries = maxEntries;
        this.validityType = validityType;
        this.validityTimeUnit = validityTimeUnit;
        this.validityUnits = validityUnits;
        this.validityFrom = validityFrom;
        this.validityTo = validityTo;
        this.usageType = usageType;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.fileBlobId = fileBlobId;
        this.paymentProxies = paymentProxies;
        this.timeZone = timeZone;
        this.supportsTicketsGeneration = supportsTicketsGeneration;
    }

    public int getPriceCts() {
        return MonetaryUtil.unitToCents(price, currency);
    }

    public DateTimeModification getValidityFromModel() {
        return DateTimeModification.fromZonedDateTime(validityFrom);
    }

    public DateTimeModification getValidityToModel() {
        return DateTimeModification.fromZonedDateTime(validityTo);
    }

    public DateTimeModification getOnSaleFromModel() {
        return DateTimeModification.fromZonedDateTime(onSaleFrom);
    }

    public DateTimeModification getOnSaleToModel() {
        return DateTimeModification.fromZonedDateTime(onSaleTo);
    }

    public String getPublicIdentifier() {
        return getId().toString();
    }

    public static SubscriptionDescriptorModification fromModel(SubscriptionDescriptor subscriptionDescriptor) {
        return new SubscriptionDescriptorModification(
            subscriptionDescriptor.getId(),
            subscriptionDescriptor.getTitle(),
            subscriptionDescriptor.getDescription(),
            subscriptionDescriptor.getMaxAvailable(),
            subscriptionDescriptor.getOnSaleFrom(),
            subscriptionDescriptor.getOnSaleTo(),
            MonetaryUtil.centsToUnit(subscriptionDescriptor.getPrice(), subscriptionDescriptor.getCurrency()),
            subscriptionDescriptor.getVat(),
            subscriptionDescriptor.getVatStatus(),
            subscriptionDescriptor.getCurrency(),
            subscriptionDescriptor.isPublic(),
            subscriptionDescriptor.getOrganizationId(),
            subscriptionDescriptor.getMaxEntries(),
            subscriptionDescriptor.getValidityType(),
            subscriptionDescriptor.getValidityTimeUnit(),
            subscriptionDescriptor.getValidityUnits(),
            subscriptionDescriptor.getValidityFrom(),
            subscriptionDescriptor.getValidityTo(),
            subscriptionDescriptor.getUsageType(),
            subscriptionDescriptor.getTermsAndConditionsUrl(),
            subscriptionDescriptor.getPrivacyPolicyUrl(),
            subscriptionDescriptor.getFileBlobId(),
            subscriptionDescriptor.getPaymentProxies(),
            subscriptionDescriptor.getZoneId(),
            subscriptionDescriptor.isSupportsTicketsGeneration());
    }
}
