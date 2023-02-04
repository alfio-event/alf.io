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
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonCreator
    public EventCreationRequest(@JsonProperty("title") String title,
                                @JsonProperty("slug") String slug,
                                @JsonProperty("description") List<DescriptionRequest> description,
                                @JsonProperty("format") Event.EventFormat format,
                                @JsonProperty("location") LocationRequest location,
                                @JsonProperty("timezone") String timezone,
                                @JsonProperty("startDate") LocalDateTime startDate,
                                @JsonProperty("endDate") LocalDateTime endDate,
                                @JsonProperty("websiteUrl") String websiteUrl,
                                @JsonProperty("termsAndConditionsUrl") String termsAndConditionsUrl,
                                @JsonProperty("privacyPolicyUrl") String privacyPolicyUrl,
                                @JsonProperty("imageUrl") String imageUrl,
                                @JsonProperty("tickets") TicketRequest tickets,
                                @JsonProperty("extensionSettings") List<ExtensionSetting> extensionSettings,
                                @JsonProperty("additionalInfo") List<AttendeeAdditionalInfoRequest> additionalInfo) {
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
        String eventSlug = this.slug;
        if(StringUtils.isBlank(eventSlug)) {
            eventSlug = slugGenerator.apply(title);
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
            eventSlug,
            title,
            organization.getId(),
            location.getFullAddress(),
            location.getCoordinate().getLatitude(),
            location.getCoordinate().getLongitude(),
            timezone,
            description.stream().collect(Collectors.toMap(DescriptionRequest::getLang,DescriptionRequest::getBody)),
            new DateTimeModification(startDate.toLocalDate(),startDate.toLocalTime()),
            new DateTimeModification(endDate.toLocalDate(),endDate.toLocalTime()),
            Boolean.TRUE.equals(tickets.freeOfCharge) ? BigDecimal.ZERO : tickets.categories.stream().map(x -> x.price).max(BigDecimal::compareTo).orElse(BigDecimal.ONE).max(BigDecimal.ONE),
            tickets.currency,
            tickets.max,
            tickets.taxPercentage,
            tickets.taxIncludedInPrice,
            tickets.paymentMethods,
            getTicketCategoryModificationList(),
            tickets.freeOfCharge,
            new LocationDescriptor(timezone, location.getCoordinate().getLatitude(), location.getCoordinate().getLongitude(), null),
            locales,
            toAdditionalFields(orEmpty(additionalInfo)),
            emptyList(),  // TODO improve API,
            AlfioMetadata.empty(),
            List.of()
        );
    }

    private List<TicketCategoryModification> getTicketCategoryModificationList() {
        var result = new ArrayList<TicketCategoryModification>();
        for (int c = 0; c < tickets.categories.size(); c++) {
            result.add(tickets.categories.get(c).toNewTicketCategoryModification(c + 1 ));
        }
        return List.copyOf(result);
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
            tickets != null && tickets.categories != null ? createFromExistingCategories(original) : null,
            tickets != null ? first(tickets.freeOfCharge,original.isFreeOfCharge()) : original.isFreeOfCharge(),
            null,
            locales,
            toAdditionalFields(orEmpty(additionalInfo)),
            emptyList(),  // TODO improve API
            AlfioMetadata.empty(),
            List.of()
        );
    }

    private List<TicketCategoryModification> createFromExistingCategories(EventWithAdditionalInfo event) {
        var result = new ArrayList<TicketCategoryModification>(tickets.categories.size());
        for(int c = 0; c < tickets.categories.size(); c++) {
            var categoryRequest = tickets.categories.get(c);
            var existing = findExistingCategory(event.getTicketCategories(), categoryRequest.getName(), categoryRequest.getId());
            if (existing.isPresent()) {
                var category = existing.get();
                result.add(categoryRequest.toExistingTicketCategoryModification(category.getId(), category.getOrdinal()));
            } else {
                result.add(categoryRequest.toNewTicketCategoryModification(c + 1));
            }
        }
        return List.copyOf(result);
    }

    public static Optional<TicketCategoryWithAdditionalInfo> findExistingCategory(List<TicketCategoryWithAdditionalInfo> categories,
                                                                                  String name,
                                                                                  Integer id) {
        return categories.stream()
            // if specified, ID takes precedence over name
            .filter(oc -> id != null ? id == oc.getId() : oc.getName().equals(name))
            .findFirst();
    }


    @Getter
    public static class DescriptionRequest {
        private final String lang;
        private final String body;

        @JsonCreator
        public DescriptionRequest(@JsonProperty("lang") String lang, @JsonProperty("body") String body) {
            this.lang = lang;
            this.body = body;
        }
    }

    @Getter
    public static class LocationRequest {
        private final String fullAddress;
        private final CoordinateRequest coordinate;

        @JsonCreator
        public LocationRequest(@JsonProperty("fullAddress") String fullAddress, @JsonProperty("coordinate") CoordinateRequest coordinate) {
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

        @JsonCreator
        public TicketRequest(@JsonProperty("freeOfCharge") Boolean freeOfCharge,
                             @JsonProperty("max") Integer max,
                             @JsonProperty("currency") String currency,
                             @JsonProperty("taxPercentage") BigDecimal taxPercentage,
                             @JsonProperty("taxIncludedInPrice") Boolean taxIncludedInPrice,
                             @JsonProperty("paymentMethods") List<PaymentProxy> paymentMethods,
                             @JsonProperty("categories") List<CategoryRequest> categories,
                             @JsonProperty("promoCodes") List<PromoCodeRequest> promoCodes) {
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

        @JsonCreator
        public CoordinateRequest(@JsonProperty("latitude") String latitude, @JsonProperty("longitude") String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    @Getter
    public static class CategoryRequest {
        private final Integer id;
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

        @JsonCreator
        public CategoryRequest(@JsonProperty("id") Integer id,
                               @JsonProperty("name") String name,
                               @JsonProperty("description") List<DescriptionRequest> description,
                               @JsonProperty("maxTickets") Integer maxTickets,
                               @JsonProperty("accessRestricted") boolean accessRestricted,
                               @JsonProperty("price") BigDecimal price,
                               @JsonProperty("startSellingDate") LocalDateTime startSellingDate,
                               @JsonProperty("endSellingDate") LocalDateTime endSellingDate,
                               @JsonProperty("accessCode") String accessCode,
                               @JsonProperty("customValidity") CustomTicketValidityRequest customValidity,
                               @JsonProperty("groupLink") GroupLinkRequest groupLink,
                               @JsonProperty("ticketAccessType") TicketCategory.TicketAccessType ticketAccessType) {
            this.id = id;
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

        TicketCategoryModification toNewTicketCategoryModification(int ordinal) {
            return toExistingTicketCategoryModification(null, ordinal);
        }

        TicketCategoryModification toExistingTicketCategoryModification(Integer categoryId, int ordinal) {
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
                ordinal,
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

        @JsonCreator
        public CustomTicketValidityRequest(@JsonProperty("checkInFrom") LocalDateTime checkInFrom,
                                           @JsonProperty("checkInTo") LocalDateTime checkInTo,
                                           @JsonProperty("validityStart") LocalDateTime validityStart,
                                           @JsonProperty("validityEnd") LocalDateTime validityEnd) {
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

        @JsonCreator
        public PromoCodeRequest(@JsonProperty("name") String name,
                                @JsonProperty("validFrom") LocalDateTime validFrom,
                                @JsonProperty("validTo") LocalDateTime validTo,
                                @JsonProperty("discountType") PromoCodeDiscount.DiscountType discountType,
                                @JsonProperty("discount") int discount) {
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

        @JsonCreator
        public ExtensionSetting(@JsonProperty("extensionId") String extensionId,
                                @JsonProperty("key") String key,
                                @JsonProperty("value") String value) {
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

        @JsonCreator
        public GroupLinkRequest(@JsonProperty("groupId") Integer groupId,
                                @JsonProperty("type") LinkedGroup.Type type,
                                @JsonProperty("matchType") LinkedGroup.MatchType matchType,
                                @JsonProperty("maxAllocation") Integer maxAllocation) {
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

        @JsonCreator
        public AttendeeAdditionalInfoRequest(@JsonProperty("ordinal") Integer ordinal,
                                             @JsonProperty("name") String name,
                                             @JsonProperty("type") AdditionalInfoType type,
                                             @JsonProperty("required") Boolean required,
                                             @JsonProperty("label") List<DescriptionRequest> label,
                                             @JsonProperty("placeholder") List<DescriptionRequest> placeholder,
                                             @JsonProperty("restrictedValues") List<RestrictedValueRequest> restrictedValues,
                                             @JsonProperty("contentLength") ContentLengthRequest contentLength) {
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
            List<EventModification.RestrictedValue> cleanRestrictedValues = null;
            if(!isEmpty(this.restrictedValues)) {
                cleanRestrictedValues = this.restrictedValues.stream().map(rv -> new EventModification.RestrictedValue(rv.value, rv.enabled)).toList();
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
                cleanRestrictedValues,
                toDescriptionMap(orEmpty(label), orEmpty(placeholder), orEmpty(this.restrictedValues)),
                null,//TODO: linkedAdditionalService
                null);//TODO: linkedCategoryIds
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
    }

    private static <T> List<T> orEmpty(List<T> input) {
        return isEmpty(input) ? emptyList() : input;
    }


    private static List<EventModification.AdditionalField> toAdditionalFields(List<AttendeeAdditionalInfoRequest> additionalInfoRequests) {
        if(isEmpty(additionalInfoRequests)) {
            return emptyList();
        }
        AtomicInteger counter = new AtomicInteger(1);
        return additionalInfoRequests.stream().map(air -> air.toAdditionalField(counter.getAndIncrement())).toList();
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

        @JsonCreator
        public ContentLengthRequest(@JsonProperty("min") Integer min, @JsonProperty("max") Integer max) {
            this.min = min;
            this.max = max;
        }
    }

    @Getter
    public static class RestrictedValueRequest {
        private final String value;
        private final Boolean enabled;
        private final List<DescriptionRequest> descriptions;

        @JsonCreator
        public RestrictedValueRequest(@JsonProperty("value") String value,
                                      @JsonProperty("enabled") Boolean enabled,
                                      @JsonProperty("descriptions") List<DescriptionRequest> descriptions) {
            this.value = value;
            this.enabled = enabled;
            this.descriptions = descriptions;
        }
    }
}