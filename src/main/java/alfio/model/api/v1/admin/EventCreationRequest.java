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

import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.EventWithAdditionalInfo;
import alfio.model.PromoCodeDiscount;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class EventCreationRequest{
    private String title;
    private String slug;
    private List<DescriptionRequest> description;
    private LocationRequest location;
    private String timezone;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String websiteUrl;
    private String termsAndConditionsUrl;
    private String imageUrl;
    private TicketRequest tickets;

    public EventModification toEventModification(Organization organization, Function<String,String> slugGenerator, String imageRef) {
        if(StringUtils.isBlank(slug)) {
            slug = slugGenerator.apply(title);
        }

        int locales = description.stream()
            .map((x) -> ContentLanguage.ALL_LANGUAGES.stream().filter((l)-> l.getFlag().equals(x.lang)).findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .mapToInt(ContentLanguage::getValue).reduce(0,(x,y) -> x | y);

        return new EventModification(
            null,
            Event.EventType.INTERNAL,
            websiteUrl,
            null,
            termsAndConditionsUrl,
            null,
            null,
            imageRef,
            slug,
            title,
            organization.getId(),
            location.getFullAddress(),
            location.getCoordinate().getLatitude(),
            location.getCoordinate().getLongitude(),
            timezone,
            description.stream().collect(Collectors.toMap(DescriptionRequest::getLang,DescriptionRequest::getBody)),
            new DateTimeModification(startDate.toLocalDate(),startDate.toLocalTime()),
            new DateTimeModification(endDate.toLocalDate(),endDate.toLocalTime()),
            tickets.categories.stream().map((x) -> x.price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
            tickets.currency,
            tickets.max,
            tickets.VAT,
            tickets.VATIncluded,
            tickets.paymentsMethods,
            tickets.categories.stream().map(CategoryRequest::toTicketCategoryModification).collect(Collectors.toList()),
            tickets.freeOfCharge,
            null,
            locales,
            Collections.emptyList(), // TODO improve API
            Collections.emptyList()  // TODO improve API
        );
    }

    private static <T> T first(T value,T other) {
        return Optional.ofNullable(value).orElse(other);
    }


    public EventModification toEventModificationUpdate(EventWithAdditionalInfo original, Organization organization, String imageRef) {

        int locales = original.getLocales();
        if(description != null){
            locales = description.stream()
                .map((x) -> ContentLanguage.ALL_LANGUAGES.stream().filter((l) -> l.getFlag().equals(x.lang)).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(ContentLanguage::getValue).reduce(0, (x, y) -> x | y);
        }



        //TODO merge ticket categories
        original.getTicketCategories();



        return new EventModification(
            original.getId(),
            Event.EventType.INTERNAL,
            first(websiteUrl,original.getWebsiteUrl()),
            null,
            first(termsAndConditionsUrl,original.getWebsiteUrl()),
            null,
            null,
            first(imageRef,original.getFileBlobId()),
            original.getShortName(),
            first(title,original.getDisplayName()),
            organization.getId(),
            location != null ? first(location.getFullAddress(),original.getLocation()) : original.getLocation(),
            location != null && location.getCoordinate() != null ? location.getCoordinate().getLatitude() : original.getLatitude(),
            location != null && location.getCoordinate() != null ? location.getCoordinate().getLongitude() : original.getLongitude(),
            first(timezone,original.getTimeZone()),
            description != null ? description.stream().collect(Collectors.toMap(DescriptionRequest::getLang,DescriptionRequest::getBody)) : null,
            startDate != null ? new DateTimeModification(startDate.toLocalDate(),startDate.toLocalTime()) : new DateTimeModification(original.getBegin().toLocalDate(),original.getEnd().toLocalTime()),
            endDate != null ? new DateTimeModification(endDate.toLocalDate(),endDate.toLocalTime()) : new DateTimeModification(original.getEnd().toLocalDate(),original.getEnd().toLocalTime()),
            tickets != null && tickets.categories != null ? tickets.categories.stream().map((x) -> x.price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).max(original.getRegularPrice()) : original.getRegularPrice(),
            tickets != null ? first(tickets.currency,original.getCurrency()) : original.getCurrency(),
            tickets != null ? tickets.max : original.getAvailableSeats(),
            tickets != null ? first(tickets.VAT,original.getVat()) : original.getVat(),
            tickets != null ? first(tickets.VATIncluded,original.isVatIncluded()) : original.isVatIncluded(),
            tickets != null ? first(tickets.paymentsMethods,original.getAllowedPaymentProxies()) : original.getAllowedPaymentProxies(),
            tickets != null && tickets.categories != null ? tickets.categories.stream().map(CategoryRequest::toTicketCategoryModification).collect(Collectors.toList()) : null, // should use the merged version
            tickets != null ? first(tickets.freeOfCharge,original.isFreeOfCharge()) : original.isFreeOfCharge(),
            null,
            locales,
            Collections.emptyList(), // TODO improve API
            Collections.emptyList()  // TODO improve API
        );
    }


    @Getter
    @AllArgsConstructor
    public static class DescriptionRequest{
        private String lang;
        private String body;
    }

    @Getter
    @AllArgsConstructor
    public static class LocationRequest{
        private String fullAddress;
        private CoordinateRequest coordinate;
    }

    @Getter
    @AllArgsConstructor
    public static class TicketRequest{
        private Boolean freeOfCharge;
        private Integer max;
        private String currency;
        private BigDecimal VAT;
        private Boolean VATIncluded;
        private List<PaymentProxy> paymentsMethods;
        private List<CategoryRequest> categories;
        private List<PromoCodeRequest> promoCodes;
    }

    @Getter
    @AllArgsConstructor
    public static class CoordinateRequest{
        private String latitude;
        private String longitude;
    }

    @Getter
    @AllArgsConstructor
    public static class CategoryRequest{
        private String name;
        private List<DescriptionRequest> description;
        private Integer maxTickets;
        private boolean accessRestricted;
        private BigDecimal price;
        private LocalDateTime startSellingDate;
        private LocalDateTime endSellingDate;
        private String accessCode;
        private CustomTicketValidityRequest customValidity;

        TicketCategoryModification toTicketCategoryModification() {
            int capacity = Optional.ofNullable(maxTickets).orElse(0);

            Optional<CustomTicketValidityRequest> customValidityOpt = Optional.ofNullable(customValidity);

            return new TicketCategoryModification(
                null,
                name,
                capacity,
                new DateTimeModification(startSellingDate.toLocalDate(),startSellingDate.toLocalTime()),
                new DateTimeModification(endSellingDate.toLocalDate(),endSellingDate.toLocalTime()),
                description.stream().collect(Collectors.toMap(DescriptionRequest::getLang,DescriptionRequest::getBody)),
                price,
                accessRestricted,
                null,
                capacity > 0,
                accessCode,
                customValidityOpt.flatMap((x) -> Optional.ofNullable(x.checkInFrom)).map((x) -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap((x) -> Optional.ofNullable(x.checkInTo)).map((x) -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap((x) -> Optional.ofNullable(x.validityStart)).map((x) -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap((x) -> Optional.ofNullable(x.validityEnd)).map((x) -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null)
            );
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CustomTicketValidityRequest{
        private LocalDateTime checkInFrom;
        private LocalDateTime checkInTo;
        private LocalDateTime validityStart;
        private LocalDateTime validityEnd;
    }

    @Getter
    @AllArgsConstructor
    public static class PromoCodeRequest {
        private String name;
        private LocalDateTime validFrom;
        private LocalDateTime validTo;
        private PromoCodeDiscount.DiscountType discountType;
        private int discount;
    }
}