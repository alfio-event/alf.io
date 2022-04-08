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
public class EventCreationRequest{
    private final String title;
    private final String slug;
    private final List<DescriptionRequest> description;
    private final Event.EventFormat format;
    private final LocationRequest location;
    private final String timezone;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final String websiteUrl;
    private final String termsAndConditionsUrl;
    private final String privacyPolicyUrl;
    private final String imageUrl;
    private final TicketRequest tickets;
    private final List<ExtensionSetting> extensionSettings;
    private final List<AttendeeAdditionalInfoRequest> additionalInfo;

    public EventCreationRequest(String title,
                                String slug,
                                List<DescriptionRequest> description,
                                Event.EventFormat format,
                                LocationRequest location,
                                String timezone,
                                LocalDateTime startDate,
                                LocalDateTime endDate,
                                String websiteUrl,
                                String termsAndConditionsUrl,
                                String privacyPolicyUrl,
                                String imageUrl,
                                TicketRequest tickets,
                                List<ExtensionSetting> extensionSettings,
                                List<AttendeeAdditionalInfoRequest> additionalInfo) {
        this.title = title;
        this.slug = slug;
        this.description = description;
        this.format = format;
        this.location = location;
        this.timezone = timezone;
        this.startDate = startDate;
        this.endDate = endDate;
        this.websiteUrl = websiteUrl;
        this.termsAndConditionsUrl = termsAndConditionsUrl;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.imageUrl = imageUrl;
        this.tickets = tickets;
        this.extensionSettings = extensionSettings;
        this.additionalInfo = additionalInfo;
    }

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
    public static class DescriptionRequest {
        private final String lang;
        private final String body;

        public DescriptionRequest(String lang, String body) {
            this.lang = lang;
            this.body = body;
        }
    }

    @Getter
    public static class LocationRequest {
        private final String fullAddress;
        private final CoordinateRequest coordinate;

        public LocationRequest(String fullAddress, CoordinateRequest coordinate) {
            this.fullAddress = fullAddress;
            this.coordinate = coordinate;
        }
    }

    @Getter
    public static class TicketRequest {
        private final Boolean freeOfCharge;
        private final Integer max;
        private final String currency;
        private final BigDecimal taxPercentage;
        private final Boolean taxIncludedInPrice;
        private final List<PaymentProxy> paymentMethods;
        private final List<CategoryRequest> categories;
        private final List<PromoCodeRequest> promoCodes;

        public TicketRequest(Boolean freeOfCharge,
                             Integer max,
                             String currency,
                             BigDecimal taxPercentage,
                             Boolean taxIncludedInPrice,
                             List<PaymentProxy> paymentMethods,
                             List<CategoryRequest> categories,
                             List<PromoCodeRequest> promoCodes) {
            this.freeOfCharge = freeOfCharge;
            this.max = max;
            this.currency = currency;
            this.taxPercentage = taxPercentage;
            this.taxIncludedInPrice = taxIncludedInPrice;
            this.paymentMethods = paymentMethods;
            this.categories = categories;
            this.promoCodes = promoCodes;
        }
    }

    @Getter
    public static class CoordinateRequest {
        private final String latitude;
        private final String longitude;

        public CoordinateRequest(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    @Getter
    public static class CategoryRequest {
        private final String name;
        private final List<DescriptionRequest> description;
        private final Integer maxTickets;
        private final boolean accessRestricted;
        private final BigDecimal price;
        private final LocalDateTime startSellingDate;
        private final LocalDateTime endSellingDate;
        private final String accessCode;
        private final CustomTicketValidityRequest customValidity;
        private final GroupLinkRequest groupLink;
        private final TicketCategory.TicketAccessType ticketAccessType;

        public CategoryRequest(String name,
                               List<DescriptionRequest> description,
                               Integer maxTickets,
                               boolean accessRestricted,
                               BigDecimal price,
                               LocalDateTime startSellingDate,
                               LocalDateTime endSellingDate,
                               String accessCode,
                               CustomTicketValidityRequest customValidity,
                               GroupLinkRequest groupLink,
                               TicketCategory.TicketAccessType ticketAccessType) {
            this.name = name;
            this.description = description;
            this.maxTickets = maxTickets;
            this.accessRestricted = accessRestricted;
            this.price = price;
            this.startSellingDate = startSellingDate;
            this.endSellingDate = endSellingDate;
            this.accessCode = accessCode;
            this.customValidity = customValidity;
            this.groupLink = groupLink;
            this.ticketAccessType = ticketAccessType;
        }

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
    public static class CustomTicketValidityRequest {
        private final LocalDateTime checkInFrom;
        private final LocalDateTime checkInTo;
        private final LocalDateTime validityStart;
        private final LocalDateTime validityEnd;

        public CustomTicketValidityRequest(LocalDateTime checkInFrom, LocalDateTime checkInTo, LocalDateTime validityStart, LocalDateTime validityEnd) {
            this.checkInFrom = checkInFrom;
            this.checkInTo = checkInTo;
            this.validityStart = validityStart;
            this.validityEnd = validityEnd;
        }
    }

    @Getter
    public static class PromoCodeRequest {
        private final String name;
        private final LocalDateTime validFrom;
        private final LocalDateTime validTo;
        private final PromoCodeDiscount.DiscountType discountType;
        private final int discount;

        public PromoCodeRequest(String name, LocalDateTime validFrom, LocalDateTime validTo, PromoCodeDiscount.DiscountType discountType, int discount) {
            this.name = name;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.discountType = discountType;
            this.discount = discount;
        }
    }

    @Getter
    public static class ExtensionSetting {
        private final String extensionId;
        private final String key;
        private final String value;

        public ExtensionSetting(String extensionId, String key, String value) {
            this.extensionId = extensionId;
            this.key = key;
            this.value = value;
        }
    }

    @Getter
    public static class GroupLinkRequest {
        private final Integer groupId;
        private final LinkedGroup.Type type;
        private final LinkedGroup.MatchType matchType;
        private final Integer maxAllocation;

        public GroupLinkRequest(Integer groupId, LinkedGroup.Type type, LinkedGroup.MatchType matchType, Integer maxAllocation) {
            this.groupId = groupId;
            this.type = type;
            this.matchType = matchType;
            this.maxAllocation = maxAllocation;
        }
    }

    @Getter
    public static class AttendeeAdditionalInfoRequest {

        private final Integer ordinal;
        private final String name;
        private final AdditionalInfoType type;
        private final Boolean required;
        private final List<DescriptionRequest> label;
        private final List<DescriptionRequest> placeholder;
        private final List<RestrictedValueRequest> restrictedValues;
        private final ContentLengthRequest contentLength;

        public AttendeeAdditionalInfoRequest(Integer ordinal,
                                             String name,
                                             AdditionalInfoType type,
                                             Boolean required,
                                             List<DescriptionRequest> label,
                                             List<DescriptionRequest> placeholder,
                                             List<RestrictedValueRequest> restrictedValues,
                                             ContentLengthRequest contentLength) {
            this.ordinal = ordinal;
            this.name = name;
            this.type = type;
            this.required = required;
            this.label = label;
            this.placeholder = placeholder;
            this.restrictedValues = restrictedValues;
            this.contentLength = contentLength;
        }


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
    public static class ContentLengthRequest {
        private final Integer min;
        private final Integer max;

        public ContentLengthRequest(Integer min, Integer max) {
            this.min = min;
            this.max = max;
        }
    }

    @Getter
    public static class RestrictedValueRequest {
        private final String value;
        private final Boolean enabled;
        private final List<DescriptionRequest> descriptions;

        public RestrictedValueRequest(String value,
                                      Boolean enabled,
                                      List<DescriptionRequest> descriptions) {
            this.value = value;
            this.enabled = enabled;
            this.descriptions = descriptions;
        }
    }
}