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
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;


import java.math.BigDecimal;

import java.util.*;


@Getter
public class EventModification {

    private final Integer id;
    private final Event.EventType eventType;
    private final String websiteUrl;
    private final String externalUrl;
    private final String termsAndConditionsUrl;
    private final String imageUrl;
    private final String fileBlobId;
    private final String shortName;
    private final String displayName;
    private final int organizationId;
    private final String location;
    private final Map<String, String> description;
    private final DateTimeModification begin;
    private final DateTimeModification end;
    private final BigDecimal regularPrice;
    private final String currency;
    private final int availableSeats;
    private final BigDecimal vat;
    private final boolean vatIncluded;
    private final List<PaymentProxy> allowedPaymentProxies;
    private final List<TicketCategoryModification> ticketCategories;
    private final boolean freeOfCharge;
    private final LocationDescriptor locationDescriptor;
    private final int locales;

    private final List<AdditionalField> ticketFields;

    @JsonCreator
    public EventModification(@JsonProperty("id") Integer id,
                             @JsonProperty("type") Event.EventType eventType,
                             @JsonProperty("websiteUrl") String websiteUrl,
                             @JsonProperty("external") String externalUrl,
                             @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                             @JsonProperty("imageUrl") String imageUrl,
                             @JsonProperty("fileBlobId") String fileBlobId,
                             @JsonProperty("shortName") String shortName,
                             @JsonProperty("displayName") String displayName,
                             @JsonProperty("organizationId") int organizationId,
                             @JsonProperty("location") String location,
                             @JsonProperty("description") Map<String, String> description,
                             @JsonProperty("begin") DateTimeModification begin,
                             @JsonProperty("end") DateTimeModification end,
                             @JsonProperty("regularPrice") BigDecimal regularPrice,
                             @JsonProperty("currency") String currency,
                             @JsonProperty("availableSeats") int availableSeats,
                             @JsonProperty("vat") BigDecimal vat,
                             @JsonProperty("vatIncluded") boolean vatIncluded,
                             @JsonProperty("allowedPaymentProxies") List<PaymentProxy> allowedPaymentProxies,
                             @JsonProperty("ticketCategories") List<TicketCategoryModification> ticketCategories,
                             @JsonProperty("freeOfCharge") boolean freeOfCharge,
                             @JsonProperty("geoLocation") LocationDescriptor locationDescriptor,
                             @JsonProperty("locales") int locales,
                             @JsonProperty("ticketFields") List<AdditionalField> ticketFields) {
        this.id = id;
        this.eventType = eventType;
        this.websiteUrl = websiteUrl;
        this.externalUrl = externalUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.imageUrl = imageUrl;
        this.fileBlobId = fileBlobId;
        this.shortName = shortName;
        this.displayName = displayName;
        this.organizationId = organizationId;
        this.location = location;
        this.description = description;
        this.begin = begin;
        this.end = end;
        this.regularPrice = regularPrice;
        this.currency = currency;
        this.availableSeats = availableSeats;
        this.vat = vat;
        this.vatIncluded = vatIncluded;
        this.locationDescriptor = locationDescriptor;
        this.allowedPaymentProxies = Optional.ofNullable(allowedPaymentProxies).orElse(Collections.<PaymentProxy>emptyList());
        this.ticketCategories = ticketCategories;
        this.freeOfCharge = freeOfCharge;
        this.locales = locales;
        this.ticketFields = ticketFields;
    }

    public int getPriceInCents() {
        return freeOfCharge ? 0 : MonetaryUtil.unitToCents(regularPrice);
    }

    public LocationDescriptor getGeolocation() {
        return locationDescriptor;
    }

    public boolean isInternal() {
        return eventType == Event.EventType.INTERNAL;
    }


    @Getter
    public static class AdditionalField {
        private final int order;
        private final String name;
        private final String type;
        private final boolean required;

        private final Integer minLength;
        private final Integer maxLength;
        private final List<RestrictedValue> restrictedValues;

        // locale -> description
        private final Map<String, Description> description;


        public AdditionalField(@JsonProperty("order") int order,
                               @JsonProperty("name") String name,
                               @JsonProperty("type") String type,
                               @JsonProperty("required") boolean required,
                               @JsonProperty("minLength") Integer minLength,
                               @JsonProperty("maxLength") Integer maxLength,
                               @JsonProperty("restrictedValues") List<RestrictedValue> restrictedValues,
                               @JsonProperty("description") Map<String, Description> description) {
            this.order = order;
            this.name = name;
            this.type = type;
            this.required = required;
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.restrictedValues = restrictedValues;
            this.description = description;
        }
    }

    @Getter
    public static class Description {
        private final String label;
        private final String placeholder;

        //restricted value -> description
        private final Map<String, String> restrictedValues;

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

        public RestrictedValue(@JsonProperty("value") String value) {
            this.value = value;
        }
    }
}
