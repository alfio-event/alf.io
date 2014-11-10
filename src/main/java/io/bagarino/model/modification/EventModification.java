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
package io.bagarino.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.bagarino.model.Event;
import io.bagarino.model.TicketCategory;
import io.bagarino.model.modification.support.LocationDescriptor;
import io.bagarino.model.transaction.PaymentProxy;
import io.bagarino.util.MonetaryUtil;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import static io.bagarino.model.modification.DateTimeModification.fromZonedDateTime;
import static io.bagarino.model.modification.support.LocationDescriptor.fromGeoData;
import static java.util.stream.Collectors.toList;

@Getter
public class EventModification {

    private final Integer id;
    private final String websiteUrl;
    private final String termsAndConditionsUrl;
    private final String imageUrl;
    private final String shortName;
    private final int organizationId;
    private final String location;
    private final String description;
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

    @JsonCreator
    public EventModification(@JsonProperty("id") Integer id,
                             @JsonProperty("websiteUrl") String websiteUrl,
                             @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                             @JsonProperty("imageUrl") String imageUrl,
                             @JsonProperty("shortName") String shortName,
                             @JsonProperty("organizationId") int organizationId,
                             @JsonProperty("location") String location,
                             @JsonProperty("description") String description,
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
                             @JsonProperty("geoLocation") LocationDescriptor locationDescriptor) {
        this.id = id;
        this.websiteUrl = websiteUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.imageUrl = imageUrl;
        this.shortName = shortName;
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
    }

    public int getPriceInCents() {
        return freeOfCharge ? 0 : MonetaryUtil.unitToCents(regularPrice);
    }

    public LocationDescriptor getGeolocation() {
        return locationDescriptor;
    }

    public static EventModification fromEvent(Event event, List<TicketCategory> ticketCategories, Optional<String> mapsApiKey) {
        final ZoneId zoneId = event.getZoneId();
        return new EventModification(event.getId(),
                event.getWebsiteUrl(),
                event.getTermAndConditionsUrl(),
                event.getImageUrl(),
                event.getShortName(),
                event.getOrganizationId(),
                event.getLocation(),
                event.getDescription(),
                fromZonedDateTime(event.getBegin()),
                fromZonedDateTime(event.getEnd()),
                event.getRegularPrice(),
                event.getCurrency(),
                event.getAvailableSeats(),
                event.getVat(),
                event.isVatIncluded(),
                event.getAllowedPaymentProxies(),
                ticketCategories.stream().map(tc -> TicketCategoryModification.fromTicketCategory(tc, zoneId)).collect(toList()),
                event.isFreeOfCharge(),
                fromGeoData(Pair.of(event.getLatitude(), event.getLongitude()), TimeZone.getTimeZone(zoneId), mapsApiKey));
    }
}
