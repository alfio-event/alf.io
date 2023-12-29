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
package alfio.model.api.v1.admin;

import alfio.model.PriceContainer;
import alfio.model.api.v1.admin.subscription.CustomPeriodTerm;
import alfio.model.api.v1.admin.subscription.EntryBasedTerm;
import alfio.model.api.v1.admin.subscription.StandardPeriodTerm;
import alfio.model.api.v1.admin.subscription.SubscriptionTerm;
import alfio.model.modification.AdditionalFieldRequest;
import alfio.model.modification.SubscriptionDescriptorModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.model.subscription.SubscriptionDescriptor.SubscriptionUsageType;
import alfio.model.transaction.PaymentProxy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static alfio.util.LocaleUtil.atZone;
import static java.util.Objects.requireNonNullElse;

public class SubscriptionDescriptorModificationRequest {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionDescriptorModificationRequest.class);
    public static final String TERM_STANDARD = "standard";
    public static final String TERM_NUM_ENTRIES = "numEntries";
    public static final String TERM_CUSTOM = "custom";

    private final SubscriptionUsageType usageType;
    private final SubscriptionTerm term;

    private final List<DescriptionRequest> title;
    private final List<DescriptionRequest> description;
    private final Integer maxAvailable;
    private final LocalDateTime onSaleFrom;
    private final LocalDateTime onSaleTo;
    private final BigDecimal price;
    private final BigDecimal taxPercentage;
    private final PriceContainer.VatStatus taxPolicy;
    private final String currencyCode;
    private final Boolean isPublic;
    private final String imageUrl;
    private final String termType;

    private final String termsAndConditionsUrl;
    private final String privacyPolicyUrl;
    private final String timezone;
    private final Boolean supportsTicketsGeneration;
    private final List<PaymentProxy> paymentMethods;
    private final List<AdditionalInfoRequest> additionalInfo;




    @JsonCreator
    public SubscriptionDescriptorModificationRequest(@JsonProperty("usageType") SubscriptionUsageType usageType,
                                                     @JsonProperty("termType") String termType,
                                                     @JsonTypeInfo(use= JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "termType")
                                                     @JsonSubTypes({
                                                         // if termType is "standard", then we expect an instance of StandardPeriodTerm
                                                         @JsonSubTypes.Type(value = StandardPeriodTerm.class, name = TERM_STANDARD),
                                                         // if termType is "numEntries", then we expect an instance of EntryBasedTerm
                                                         @JsonSubTypes.Type(value = EntryBasedTerm.class, name = TERM_NUM_ENTRIES),
                                                         // if termType is "custom", then we expect an instance of CustomPeriodTerm
                                                         @JsonSubTypes.Type(value = CustomPeriodTerm.class, name = TERM_CUSTOM)
                                                     })
                                                     @JsonProperty("term") SubscriptionTerm term,
                                                     @JsonProperty("title") List<DescriptionRequest> title,
                                                     @JsonProperty("description") List<DescriptionRequest> description,
                                                     @JsonProperty("maxAvailable") Integer maxAvailable,
                                                     @JsonProperty("onSaleFrom") LocalDateTime onSaleFrom,
                                                     @JsonProperty("onSaleTo") LocalDateTime onSaleTo,
                                                     @JsonProperty("price") BigDecimal price,
                                                     @JsonProperty("taxPercentage") BigDecimal taxPercentage,
                                                     @JsonProperty("taxPolicy") PriceContainer.VatStatus taxPolicy,
                                                     @JsonProperty("currencyCode") String currencyCode,
                                                     @JsonProperty("isPublic") Boolean isPublic,
                                                     @JsonProperty("imageUrl") String imageUrl,
                                                     @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                                                     @JsonProperty("privacyPolicyUrl") String privacyPolicyUrl,
                                                     @JsonProperty("timezone") String timezone,
                                                     @JsonProperty("supportsTicketsGeneration") Boolean supportsTicketsGeneration,
                                                     @JsonProperty("paymentMethods") List<PaymentProxy> paymentMethods,
                                                     @JsonProperty("additionalInfo") List<AdditionalInfoRequest> additionalInfo) {
        this.usageType = usageType;
        this.termType = termType;
        this.term = term;
        this.title = requireNonNullElse(title, List.of());
        this.description = requireNonNullElse(description, List.of());
        this.maxAvailable = maxAvailable;
        this.onSaleFrom = onSaleFrom;
        this.onSaleTo = onSaleTo;
        this.price = price;
        this.taxPercentage = taxPercentage;
        this.taxPolicy = taxPolicy;
        this.currencyCode = currencyCode;
        this.isPublic = isPublic;
        this.imageUrl = imageUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.timezone = timezone;
        this.supportsTicketsGeneration = supportsTicketsGeneration;
        this.paymentMethods = requireNonNullElse(paymentMethods, List.of());
        this.additionalInfo = requireNonNullElse(additionalInfo, List.of());
    }

    public Result<SubscriptionDescriptorModification> toDescriptorModification(UUID id, int organizationId, String fileBlobId) {
        var zoneIdOptional = getZoneId();
        return new Result.Builder<SubscriptionDescriptorModification>()
            .checkPrecondition(zoneIdOptional::isPresent, ErrorCode.custom("timezone", "Timezone is mandatory"))
            .checkPrecondition(() -> usageType != null, ErrorCode.custom("usageType", "UsageType is mandatory"))
            .checkPrecondition(() -> term != null && term.validate(), ErrorCode.custom("term", "Term is not valid"))
            .build(() -> {
                var zoneId = zoneIdOptional.orElseThrow();
                return new SubscriptionDescriptorModification(
                    id,
                    title.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody)),
                    description.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody)),
                    requireNonNullElse(maxAvailable, -1),
                    atZone(onSaleFrom, zoneId),
                    atZone(onSaleTo, zoneId),
                    price,
                    taxPercentage,
                    taxPolicy,
                    currencyCode,
                    Boolean.TRUE.equals(isPublic),
                    organizationId,
                    requireNonNullElse(term.getNumEntries(), -1),
                    getValidityType(),
                    term.getTimeUnit(),
                    term.getUnits(),
                    atZone(term.getValidityFrom(), zoneId),
                    atZone(term.getValidityTo(), zoneId),
                    usageType,
                    termsAndConditionsUrl,
                    privacyPolicyUrl,
                    fileBlobId,
                    paymentMethods,
                    zoneId,
                    Boolean.TRUE.equals(supportsTicketsGeneration)
                );
            });
    }

    public Result<List<AdditionalFieldRequest>> toAdditionalFieldsRequest() {
        return new Result.Builder<List<AdditionalFieldRequest>>()
            .checkPrecondition(() -> additionalInfo.isEmpty() || additionalInfo.stream().allMatch(AdditionalInfoRequest::isValid), ErrorCode.custom("additionalInfo", "Additional info not valid"))
            .build(() -> IntStream.range(0, additionalInfo.size())
                    .mapToObj(i -> additionalInfo.get(i).toAdditionalField(i+1))
                    .collect(Collectors.toList()));
    }

    private Optional<ZoneId> getZoneId() {
        if (StringUtils.isEmpty(timezone)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZoneId.of(timezone));
        } catch(Exception ex) {
            log.warn("Error while parsing timezone", ex);
            return Optional.empty();
        }
    }

    private SubscriptionDescriptor.SubscriptionValidityType getValidityType() {
        if(TERM_STANDARD.equals(termType)) {
            return SubscriptionDescriptor.SubscriptionValidityType.STANDARD;
        } else if(TERM_CUSTOM.equals(termType)) {
            return SubscriptionDescriptor.SubscriptionValidityType.CUSTOM;
        } else if(TERM_NUM_ENTRIES.equals(termType)) {
            return SubscriptionDescriptor.SubscriptionValidityType.NOT_SET;
        }
        return null;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
