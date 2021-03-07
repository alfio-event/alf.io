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

import alfio.model.*;
import alfio.model.group.LinkedGroup;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

@Getter
@AllArgsConstructor
public class EventCreationRequest{
    private String title;
    private String slug;
    private List<DescriptionRequest> description;
    private Event.EventFormat format;
    private LocationRequest location;
    private String timezone;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String websiteUrl;
    private String termsAndConditionsUrl;
    private String privacyPolicyUrl;
    private String imageUrl;
    private TicketRequest tickets;
    private List<ExtensionSetting> extensionSettings;
    private List<AttendeeAdditionalInfoRequest> additionalInfo;

    public EventModification toEventModification(Organization organization, UnaryOperator<String> slugGenerator, String imageRef) {
        String slug = this.slug;
        if(StringUtils.isBlank(slug)) {
            slug = slugGenerator.apply(title);
        }

        int locales = description.stream()
            .map(x -> ContentLanguage.ALL_LANGUAGES.stream().filter(l-> l.getLanguage().equals(x.lang)).findFirst())
            .filter(Optional::isPresent)
            .map(Optional::get)
            .mapToInt(ContentLanguage::getValue).reduce(0,(x,y) -> x | y);

        return new EventModification(
            null,
            Objects.requireNonNullElse(format, Event.EventFormat.IN_PERSON),
            websiteUrl,
            null,
            termsAndConditionsUrl,
            StringUtils.trimToNull(privacyPolicyUrl),
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
            tickets.freeOfCharge ? BigDecimal.ZERO : tickets.categories.stream().map(x -> x.price).max(BigDecimal::compareTo).orElse(BigDecimal.ONE).max(BigDecimal.ONE),
            tickets.currency,
            tickets.max,
            tickets.taxPercentage,
            tickets.taxIncludedInPrice,
            tickets.paymentMethods,
            tickets.categories.stream().map(CategoryRequest::toTicketCategoryModification).collect(Collectors.toList()),
            tickets.freeOfCharge,
            new LocationDescriptor(timezone, location.getCoordinate().getLatitude(), location.getCoordinate().getLongitude(), null),
            locales,
            toAdditionalFields(orEmpty(additionalInfo)),
            emptyList(),  // TODO improve API,
            AlfioMetadata.empty(),
            List.of()
        );
    }

    private static <T> T first(T value,T other) {
        return Optional.ofNullable(value).orElse(other);
    }


    public EventModification toEventModificationUpdate(EventWithAdditionalInfo original, Organization organization, String imageRef) {

        int locales = original.getLocales();
        if(description != null){
            locales = description.stream()
                .map(x -> ContentLanguage.ALL_LANGUAGES.stream().filter(l -> l.getLanguage().equals(x.lang)).findFirst())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .mapToInt(ContentLanguage::getValue).reduce(0, (x, y) -> x | y);
        }


        return new EventModification(
            original.getId(),
            original.getFormat(),
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
            tickets != null && tickets.categories != null ? tickets.categories.stream().map(x -> x.price).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).max(original.getRegularPrice()) : original.getRegularPrice(),
            tickets != null ? first(tickets.currency,original.getCurrency()) : original.getCurrency(),
            tickets != null ? tickets.max : original.getAvailableSeats(),
            tickets != null ? first(tickets.taxPercentage,original.getVat()) : original.getVat(),
            tickets != null ? first(tickets.taxIncludedInPrice,original.isVatIncluded()) : original.isVatIncluded(),
            tickets != null ? first(tickets.paymentMethods, original.getAllowedPaymentProxies()) : original.getAllowedPaymentProxies(),
            tickets != null && tickets.categories != null ? tickets.categories.stream().map(tc -> tc.toTicketCategoryModification(findCategoryId(original, tc))).collect(Collectors.toList()) : null,
            tickets != null ? first(tickets.freeOfCharge,original.isFreeOfCharge()) : original.isFreeOfCharge(),
            null,
            locales,
            toAdditionalFields(orEmpty(additionalInfo)),
            emptyList(),  // TODO improve API
            AlfioMetadata.empty(),
            List.of()
        );
    }

    private static Integer findCategoryId(EventWithAdditionalInfo event, CategoryRequest categoryRequest) {
        return event.getTicketCategories().stream()
            .filter(tc -> tc.getName().equals(categoryRequest.getName()))
            .map(TicketCategoryWithAdditionalInfo::getId)
            .findFirst()
            .orElse(null);
    }


    @Getter
    @AllArgsConstructor
    public static class DescriptionRequest {
        private String lang;
        private String body;
    }

    @Getter
    @AllArgsConstructor
    public static class LocationRequest {
        private String fullAddress;
        private CoordinateRequest coordinate;
    }

    @Getter
    @AllArgsConstructor
    public static class TicketRequest {
        private Boolean freeOfCharge;
        private Integer max;
        private String currency;
        private BigDecimal taxPercentage;
        private Boolean taxIncludedInPrice;
        private List<PaymentProxy> paymentMethods;
        private List<CategoryRequest> categories;
        private List<PromoCodeRequest> promoCodes;
    }

    @Getter
    @AllArgsConstructor
    public static class CoordinateRequest {
        private String latitude;
        private String longitude;
    }

    @Getter
    @AllArgsConstructor
    public static class CategoryRequest {
        private String name;
        private List<DescriptionRequest> description;
        private Integer maxTickets;
        private boolean accessRestricted;
        private BigDecimal price;
        private LocalDateTime startSellingDate;
        private LocalDateTime endSellingDate;
        private String accessCode;
        private CustomTicketValidityRequest customValidity;
        private GroupLinkRequest groupLink;
        private TicketCategory.TicketAccessType ticketAccessType;

        TicketCategoryModification toTicketCategoryModification() {
            return toTicketCategoryModification(null);
        }

        TicketCategoryModification toTicketCategoryModification(Integer categoryId) {
            int capacity = Optional.ofNullable(maxTickets).orElse(0);

            Optional<CustomTicketValidityRequest> customValidityOpt = Optional.ofNullable(customValidity);

            return new TicketCategoryModification(
                categoryId,
                name,
                Objects.requireNonNullElse(ticketAccessType, TicketCategory.TicketAccessType.INHERIT),
                capacity,
                new DateTimeModification(startSellingDate.toLocalDate(),startSellingDate.toLocalTime()),
                new DateTimeModification(endSellingDate.toLocalDate(),endSellingDate.toLocalTime()),
                description.stream().collect(Collectors.toMap(DescriptionRequest::getLang,DescriptionRequest::getBody)),
                price,
                accessRestricted,
                null,
                capacity > 0,
                accessCode,
                customValidityOpt.flatMap(x -> Optional.ofNullable(x.checkInFrom)).map(x -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap(x -> Optional.ofNullable(x.checkInTo)).map(x -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap(x -> Optional.ofNullable(x.validityStart)).map(x -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                customValidityOpt.flatMap(x -> Optional.ofNullable(x.validityEnd)).map(x -> new DateTimeModification(x.toLocalDate(),x.toLocalTime())).orElse(null),
                0,
                null,
                null,
                AlfioMetadata.empty());
        }
    }

    @Getter
    @AllArgsConstructor
    public static class CustomTicketValidityRequest {
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

    @Getter
    @AllArgsConstructor
    public static class ExtensionSetting {
        private String extensionId;
        private String key;
        private String value;
    }

    @Getter
    @AllArgsConstructor
    public static class GroupLinkRequest {
        private Integer groupId;
        private LinkedGroup.Type type;
        private LinkedGroup.MatchType matchType;
        private Integer maxAllocation;
    }

    @Getter
    @AllArgsConstructor
    public static class AttendeeAdditionalInfoRequest {

        private Integer ordinal;
        private String name;
        private AdditionalInfoType type;
        private Boolean required;
        private List<DescriptionRequest> label;
        private List<DescriptionRequest> placeholder;
        private List<RestrictedValueRequest> restrictedValues;
        private ContentLengthRequest contentLength;


        private EventModification.AdditionalField toAdditionalField(int ordinal) {
            int position = this.ordinal != null ? this.ordinal : ordinal;
            String code = type != null ? type.code : AdditionalInfoType.GENERIC_TEXT.code;
            Integer minLength = contentLength != null ? contentLength.min : null;
            Integer maxLength = contentLength != null ? contentLength.max : null;
            List<EventModification.RestrictedValue> restrictedValues = null;
            if(!isEmpty(this.restrictedValues)) {
                restrictedValues = this.restrictedValues.stream().map(rv -> new EventModification.RestrictedValue(rv.value, rv.enabled)).collect(Collectors.toList());
            }

            return new EventModification.AdditionalField(
                position,
                null,
                name,
                code,
                Boolean.TRUE.equals(required),
                false,
                minLength,
                maxLength,
                restrictedValues,
                toDescriptionMap(orEmpty(label), orEmpty(placeholder), orEmpty(this.restrictedValues)),
                null,//TODO: linkedAdditionalService
                null);//TODO: linkedCategoryIds
        }
    }

    private static <T> List<T> orEmpty(List<T> input) {
        return isEmpty(input) ? emptyList() : input;
    }

    private static Map<String, EventModification.Description> toDescriptionMap(List<DescriptionRequest> label,
                                                                               List<DescriptionRequest> placeholder,
                                                                               List<RestrictedValueRequest> restrictedValues) {
        Map<String, String> labelsByLang = label.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody));
        Map<String, String> placeholdersByLang = placeholder.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody));
        Map<String, List<Triple<String, String, String>>> valuesByLang = restrictedValues.stream()
            .flatMap(rv -> rv.descriptions.stream().map(rvd -> Triple.of(rvd.lang, rv.value, rvd.body)))
            .collect(Collectors.groupingBy(Triple::getLeft));


        Set<String> keys = new HashSet<>(labelsByLang.keySet());
        keys.addAll(placeholdersByLang.keySet());
        keys.addAll(valuesByLang.keySet());

        return keys.stream()
            .map(lang -> {
                Map<String, String> rvsMap = valuesByLang.getOrDefault(lang, emptyList()).stream().collect(Collectors.toMap(Triple::getMiddle, Triple::getRight));
                return Pair.of(lang, new EventModification.Description(labelsByLang.get(lang), placeholdersByLang.get(lang), rvsMap));
            }).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }


    private static List<EventModification.AdditionalField> toAdditionalFields(List<AttendeeAdditionalInfoRequest> additionalInfoRequests) {
        if(isEmpty(additionalInfoRequests)) {
            return emptyList();
        }
        AtomicInteger counter = new AtomicInteger(1);
        return additionalInfoRequests.stream().map(air -> air.toAdditionalField(counter.getAndIncrement())).collect(Collectors.toList());
    }

    @Getter
    enum AdditionalInfoType {

        GENERIC_TEXT("input:text"),
        PHONE_NUMBER("input:tel"),
        MULTI_LINE_TEXT("textarea"),
        LIST_BOX("select"),
        COUNTRY("country"),
        EU_VAT_NR("vat:eu"),
        CHECKBOX("checkbox"),
        RADIO("radio");

        private final String code;

        AdditionalInfoType(String code) {
            this.code = code;
        }

    }

    public static final Set<String> WITH_RESTRICTED_VALUES = Set.of(AdditionalInfoType.LIST_BOX.code, AdditionalInfoType.CHECKBOX.code, AdditionalInfoType.RADIO.code);

    @Getter
    @AllArgsConstructor
    public static class ContentLengthRequest {
        private Integer min;
        private Integer max;
    }

    @Getter
    @AllArgsConstructor
    public static class RestrictedValueRequest {

        private String value;
        private Boolean enabled;
        private List<DescriptionRequest> descriptions;

    }


}