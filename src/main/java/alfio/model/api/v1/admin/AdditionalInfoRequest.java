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

import alfio.model.modification.AdditionalFieldRequest;
import alfio.model.modification.EventModification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Getter
@AllArgsConstructor
public class AdditionalInfoRequest {

    private Integer ordinal;
    private String name;
    private AdditionalInfoType type;
    private Boolean required;
    private List<DescriptionRequest> label;
    private List<DescriptionRequest> placeholder;
    private List<RestrictedValueRequest> restrictedValues;
    private ContentLengthRequest contentLength;

    public boolean isValid() {
        return StringUtils.isNotBlank(name)
            && type != null
            && CollectionUtils.isNotEmpty(label)
            && label.stream().allMatch(DescriptionRequest::isValid);
    }


    public AdditionalFieldRequest toAdditionalField(int ordinal) {
        int position = this.ordinal != null ? this.ordinal : ordinal;
        String code = type != null ? type.code : AdditionalInfoType.GENERIC_TEXT.code;
        Integer minLength = contentLength != null ? contentLength.min : null;
        Integer maxLength = contentLength != null ? contentLength.max : null;
        List<EventModification.RestrictedValue> restrictedValueList = null;
        if (!isEmpty(this.restrictedValues)) {
            restrictedValueList = this.restrictedValues.stream().map(rv -> new EventModification.RestrictedValue(rv.getValue(), rv.getEnabled())).collect(Collectors.toList());
        }

        return new AdditionalFieldRequest(
            position,
            null,
            name,
            code,
            Boolean.TRUE.equals(required),
            false,
            minLength,
            maxLength,
            restrictedValueList,
            toDescriptionMap(EventCreationRequest.orEmpty(label), EventCreationRequest.orEmpty(placeholder), EventCreationRequest.orEmpty(this.restrictedValues)),
            null,
            null);
    }

    private static Map<String, EventModification.Description> toDescriptionMap(List<DescriptionRequest> label,
                                                                               List<DescriptionRequest> placeholder,
                                                                               List<RestrictedValueRequest> restrictedValues) {
        Map<String, String> labelsByLang = label.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody));
        Map<String, String> placeholdersByLang = placeholder.stream().collect(Collectors.toMap(DescriptionRequest::getLang, DescriptionRequest::getBody));
        Map<String, List<Triple<String, String, String>>> valuesByLang = restrictedValues.stream()
            .flatMap(rv -> rv.getDescriptions().stream().map(rvd -> Triple.of(rvd.getLang(), rv.getValue(), rvd.getBody())))
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

    @Getter
    public enum AdditionalInfoType {

        GENERIC_TEXT("input:text"),
        PHONE_NUMBER("input:tel"),
        MULTI_LINE_TEXT("textarea"),
        LIST_BOX("select"),
        COUNTRY("country"),
        EU_VAT_NR("vat:eu"),
        CHECKBOX("checkbox"),
        RADIO("radio"),
        DATE_OF_BIRTH("input:dateOfBirth");

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
