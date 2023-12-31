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

import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


@Getter
public class EventModification {

    private final Integer id;
    private final Event.EventFormat format;
    private final String websiteUrl;
    private final String externalUrl;
    private final String termsAndConditionsUrl;
    private final String privacyPolicyUrl;
    private final String imageUrl;
    private final String fileBlobId;
    private final String shortName;
    private final String displayName;
    private final int organizationId;
    private final String location;
    private final String latitude;
    private final String longitude;
    private final String zoneId;
    private final Map<String, String> description;
    private final DateTimeModification begin;
    private final DateTimeModification end;
    private final BigDecimal regularPrice;
    private final String currency;
    private final Integer availableSeats;
    private final BigDecimal vatPercentage;
    private final boolean vatIncluded;
    private final List<PaymentProxy> allowedPaymentProxies;
    private final List<TicketCategoryModification> ticketCategories;
    private final boolean freeOfCharge;
    private final LocationDescriptor locationDescriptor;
    private final int locales;

    private final List<AdditionalFieldRequest> ticketFields;
    private final List<AdditionalService> additionalServices;

    private final AlfioMetadata metadata;

    private final List<UUID> linkedSubscriptions;

    @JsonCreator
    public EventModification(@JsonProperty("id") Integer id,
                             @JsonProperty("format") Event.EventFormat format,
                             @JsonProperty("websiteUrl") String websiteUrl,
                             @JsonProperty("external") String externalUrl,
                             @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                             @JsonProperty("privacyPolicyUrl") String privacyPolicyUrl,
                             @JsonProperty("imageUrl") String imageUrl,
                             @JsonProperty("fileBlobId") String fileBlobId,
                             @JsonProperty("shortName") String shortName,
                             @JsonProperty("displayName") String displayName,
                             @JsonProperty("organizationId") int organizationId,
                             @JsonProperty("location") String location,
                             @JsonProperty("latitude") String latitude,
                             @JsonProperty("longitude") String longitude,
                             @JsonProperty("zoneId") String zoneId,
                             @JsonProperty("description") Map<String, String> description,
                             @JsonProperty("begin") DateTimeModification begin,
                             @JsonProperty("end") DateTimeModification end,
                             @JsonProperty("regularPrice") BigDecimal regularPrice,
                             @JsonProperty("currency") String currency,
                             @JsonProperty("availableSeats") Integer availableSeats,
                             @JsonProperty("vatPercentage") BigDecimal vatPercentage,
                             @JsonProperty("vatIncluded") boolean vatIncluded,
                             @JsonProperty("allowedPaymentProxies") List<PaymentProxy> allowedPaymentProxies,
                             @JsonProperty("ticketCategories") List<TicketCategoryModification> ticketCategories,
                             @JsonProperty("freeOfCharge") boolean freeOfCharge,
                             @JsonProperty("geolocation") LocationDescriptor locationDescriptor,
                             @JsonProperty("locales") int locales,
                             @JsonProperty("ticketFields") List<AdditionalFieldRequest> ticketFields,
                             @JsonProperty("additionalServices") List<AdditionalService> additionalServices,
                             @JsonProperty("metadata") AlfioMetadata metadata,
                             @JsonProperty("linkedSubscriptions") List<UUID> linkedSubscriptions) {
        this.id = id;
        this.format = format;
        this.websiteUrl = websiteUrl;
        this.externalUrl = externalUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.imageUrl = imageUrl;
        this.fileBlobId = fileBlobId;
        this.shortName = shortName;
        this.displayName = displayName;
        this.organizationId = organizationId;
        this.location = location;
        this.latitude= latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
        this.description = description;
        this.begin = begin;
        this.end = end;
        this.regularPrice = regularPrice;
        this.currency = currency;
        this.availableSeats = availableSeats;
        this.vatPercentage = vatPercentage;
        this.vatIncluded = vatIncluded;
        this.locationDescriptor = locationDescriptor;
        this.additionalServices = additionalServices;
        this.allowedPaymentProxies = Optional.ofNullable(allowedPaymentProxies).orElse(Collections.emptyList());
        this.ticketCategories = ticketCategories;
        this.freeOfCharge = freeOfCharge;
        this.locales = locales;
        this.ticketFields = ticketFields;
        this.metadata = metadata;
        this.linkedSubscriptions = linkedSubscriptions;
    }

    public int getPriceInCents() {
        return freeOfCharge ? 0 : MonetaryUtil.unitToCents(regularPrice, currency);
    }

    public PriceContainer.VatStatus getVatStatus() {
        if(!freeOfCharge) {
            return vatIncluded ? PriceContainer.VatStatus.INCLUDED : PriceContainer.VatStatus.NOT_INCLUDED;
        }
        return PriceContainer.VatStatus.NONE;
    }

    public LocationDescriptor getGeolocation() {
        return locationDescriptor;
    }

    public boolean isOnline() {
        return format == Event.EventFormat.ONLINE;
    }

    public interface WithType {
        String getType();
    }

    public interface WithRestrictedValues extends WithType {
        List<String> getRestrictedValuesAsString();
        List<String> getDisabledValuesAsString();
    }


    public interface WithLinkedCategories extends WithType {
        List<Integer> getLinkedCategoriesIds();
    }

    @Getter
    public static class UpdateAdditionalField implements WithRestrictedValues, WithLinkedCategories {
        private final String type;
        private final boolean required;
        private final boolean readOnly;
        private final List<String> restrictedValues;
        private final Map<String, TicketFieldDescriptionModification> description;
        private final List<String> disabledValues;
        private final List<Integer> linkedCategoriesIds;

        @JsonCreator
        public UpdateAdditionalField(@JsonProperty("type") String type,
                                     @JsonProperty("required") boolean required,
                                     @JsonProperty("readOnly") boolean readOnly,
                                     @JsonProperty("restrictedValues") List<String> restrictedValues,
                                     @JsonProperty("disabledValues") List<String> disabledValues,
                                     @JsonProperty("description") Map<String, TicketFieldDescriptionModification> description,
                                     @JsonProperty("categoryIds") List<Integer> linkedCategoriesIds) {
            this.type = type;
            this.required = required;
            this.readOnly = readOnly;
            this.restrictedValues = restrictedValues;
            this.disabledValues = disabledValues;
            this.description = description;
            this.linkedCategoriesIds = linkedCategoriesIds;
        }

        @Override
        public List<String> getRestrictedValuesAsString() {
            return restrictedValues == null ? Collections.emptyList() : restrictedValues;
        }

        @Override
        public List<String> getDisabledValuesAsString() {
            return disabledValues == null ? Collections.emptyList() : disabledValues;
        }

        @Override
        public List<Integer> getLinkedCategoriesIds() {
            return linkedCategoriesIds == null ? Collections.emptyList() : linkedCategoriesIds;
        }
    }


    @Getter
    public static class Description {
        private final String label;
        private final String placeholder;

        //restricted value -> description
        private final Map<String, String> restrictedValues;

        @JsonCreator
        public Description(@JsonProperty("label") String label,
                           @JsonProperty("placeholder") String placeholder,
                           @JsonProperty("restrictedValues") Map<String, String> restrictedValues) {
            this.label = label;
            this.placeholder = placeholder;
            this.restrictedValues = restrictedValues;
        }
    }

    @Getter
    public static class RestrictedValue {
        private final String value;
        private final boolean enabled;
        @JsonCreator
        public RestrictedValue(@JsonProperty("value") String value, @JsonProperty("enabled") Boolean enabled) {
            this.value = value;
            this.enabled = ObjectUtils.firstNonNull(enabled, Boolean.TRUE);
        }
    }

    @Getter
    public static class AdditionalService {
        private final Integer id;
        private final BigDecimal price;
        private final boolean fixPrice;
        private final int ordinal;
        private final int availableQuantity;
        private final int maxQtyPerOrder;
        private final DateTimeModification inception;
        private final DateTimeModification expiration;
        private final BigDecimal vat;
        private final alfio.model.AdditionalService.VatType vatType;
        private final List<AdditionalFieldRequest> additionalServiceFields;
        private final List<AdditionalServiceText> title;
        private final List<AdditionalServiceText> description;
        private final BigDecimal finalPrice;
        private final String currency;
        private final alfio.model.AdditionalService.AdditionalServiceType type;
        private final alfio.model.AdditionalService.SupplementPolicy supplementPolicy;

        @JsonCreator
        public AdditionalService(@JsonProperty("id") Integer id,
                                 @JsonProperty("price") BigDecimal price,
                                 @JsonProperty("fixPrice") boolean fixPrice,
                                 @JsonProperty("ordinal") int ordinal,
                                 @JsonProperty("availableQuantity") Integer availableQuantity,
                                 @JsonProperty("maxQtyPerOrder") int maxQtyPerOrder,
                                 @JsonProperty("inception") DateTimeModification inception,
                                 @JsonProperty("expiration") DateTimeModification expiration,
                                 @JsonProperty("vat") BigDecimal vat,
                                 @JsonProperty("vatType") alfio.model.AdditionalService.VatType vatType,
                                 @JsonProperty("additionalServiceFields") List<AdditionalFieldRequest> additionalServiceFields,
                                 @JsonProperty("title") List<AdditionalServiceText> title,
                                 @JsonProperty("description") List<AdditionalServiceText> description,
                                 @JsonProperty("type")alfio.model.AdditionalService.AdditionalServiceType type,
                                 @JsonProperty("supplementPolicy")alfio.model.AdditionalService.SupplementPolicy supplementPolicy) {
            this(id, price, fixPrice, ordinal, availableQuantity, maxQtyPerOrder, inception, expiration, vat, vatType, additionalServiceFields, title, description, null, null, type, supplementPolicy);
        }

        private AdditionalService(Integer id,
                                  BigDecimal price,
                                  boolean fixPrice,
                                  int ordinal,
                                  Integer availableQuantity,
                                  int maxQtyPerOrder,
                                  DateTimeModification inception,
                                  DateTimeModification expiration,
                                  BigDecimal vat,
                                  alfio.model.AdditionalService.VatType vatType,
                                  List<AdditionalFieldRequest> additionalServiceFields,
                                  List<AdditionalServiceText> title,
                                  List<AdditionalServiceText> description,
                                  BigDecimal finalPrice,
                                  String currencyCode,
                                  alfio.model.AdditionalService.AdditionalServiceType type,
                                  alfio.model.AdditionalService.SupplementPolicy supplementPolicy) {
            this.id = id;
            this.price = price;
            this.fixPrice = fixPrice;
            this.ordinal = ordinal;
            this.availableQuantity = Objects.requireNonNullElse(availableQuantity, -1);
            this.maxQtyPerOrder = maxQtyPerOrder;
            this.inception = inception;
            this.expiration = expiration;
            this.vat = vat;
            this.vatType = vatType;
            this.additionalServiceFields = additionalServiceFields;
            this.title = title;
            this.description = description;
            this.finalPrice = finalPrice;
            this.currency = currencyCode;
            this.type = type;
            this.supplementPolicy = supplementPolicy;
        }

        public static Builder from(alfio.model.AdditionalService src) {
            return new Builder(src);
        }

        public static class Builder {

            private final alfio.model.AdditionalService src;
            private ZoneId zoneId;
            private List<AdditionalFieldRequest> additionalServiceFields = new ArrayList<>();
            private List<AdditionalServiceText> title = new ArrayList<>();
            private List<AdditionalServiceText> description = new ArrayList<>();
            private PriceContainer priceContainer;

            private Builder(alfio.model.AdditionalService src) {
                this.src = src;
            }

            public Builder withZoneId(ZoneId zoneId) {
                this.zoneId = zoneId;
                return this;
            }

            public Builder withPriceContainer(PriceContainer priceContainer) {
                this.priceContainer = priceContainer;
                return this;
            }

            public Builder withText(List<alfio.model.AdditionalServiceText> text) {
                Map<Boolean, List<AdditionalServiceText>> byType = text.stream()
                    .map(AdditionalServiceText::from)
                    .collect(Collectors.partitioningBy(ast -> ast.getType() == alfio.model.AdditionalServiceText.TextType.TITLE));
                this.title = byType.getOrDefault(true, this.title);
                this.description = byType.getOrDefault(false, this.description);
                return this;
            }

            public AdditionalService build() {
                Optional<PriceContainer> optionalPrice = Optional.ofNullable(this.priceContainer);
                BigDecimal finalPrice = optionalPrice.map(PriceContainer::getFinalPrice).orElse(BigDecimal.ZERO);
                String currencyCode = optionalPrice.map(PriceContainer::getCurrencyCode).orElse("");
                return new AdditionalService(src.getId(), Optional.ofNullable(src.getSrcPriceCts()).map(p -> MonetaryUtil.centsToUnit(p, src.getCurrencyCode())).orElse(BigDecimal.ZERO),
                    src.isFixPrice(), src.getOrdinal(), src.getAvailableQuantity(), src.getMaxQtyPerOrder(), DateTimeModification.fromZonedDateTime(src.getInception(zoneId)),
                    DateTimeModification.fromZonedDateTime(src.getExpiration(zoneId)), src.getVat(), src.getVatType(), additionalServiceFields, title, description, finalPrice, currencyCode, src.getType(), src.getSupplementPolicy());
            }

        }
    }

    @Getter
    public static class AdditionalServiceText {
        private final Integer id;
        private final String locale;
        private final String value;
        private final alfio.model.AdditionalServiceText.TextType type;

        public AdditionalServiceText(@JsonProperty("id") Integer id,
                                     @JsonProperty("locale") String locale,
                                     @JsonProperty("value") String value,
                                     @JsonProperty("type") alfio.model.AdditionalServiceText.TextType type) {
            this.id = id;
            this.locale = locale;
            this.value = value;
            this.type = type;
        }

        public static AdditionalServiceText from(alfio.model.AdditionalServiceText src) {
            return new AdditionalServiceText(src.getId(), src.getLocale(), src.getValue(), src.getType());
        }
    }
}
