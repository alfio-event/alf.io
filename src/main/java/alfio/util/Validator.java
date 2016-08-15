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
package alfio.util;

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.TicketFieldConfiguration;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Validator {

    private static final Pattern SIMPLE_E_MAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9]+.*?@.+?\\..+$");

    private Validator() {
    }

    public static ValidationResult validateEventHeader(Optional<Event> event, EventModification ev, Errors errors) {

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "shortName", "error.shortname");
        if(ev.getOrganizationId() < 0) {
            errors.rejectValue("organizationId", "error.organizationId");
        }
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "location", "error.location");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "websiteUrl", "error.websiteurl");

        if(isInternal(event, ev)) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "description", "error.description");
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "termsAndConditionsUrl", "error.termsandconditionsurl");
        } else {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "externalUrl", "error.externalurl");
        }

        if(ev.getFileBlobId() == null) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "imageUrl", "error.imageurl");
            if (!StringUtils.startsWith(ev.getImageUrl(), "https://")) {
                errors.rejectValue("imageUrl", "error.imageurl");
            }
        }
        return evaluateValidationResult(errors);
    }

    private static boolean isInternal(Optional<Event> event, EventModification ev) {
        return event.map(Event::getType).orElse(ev.getEventType()) == Event.EventType.INTERNAL;
    }

    public static ValidationResult validateEventPrices(Optional<Event> event, EventModification ev, Errors errors) {

        if(!isInternal(event, ev)) {
            return ValidationResult.success();
        }

        if(!ev.isFreeOfCharge()) {
            if(isCollectionEmpty(ev.getAllowedPaymentProxies())) {
                errors.rejectValue("allowedPaymentProxies", "error.allowedpaymentproxies");
            }
            if(ev.getRegularPrice() == null || BigDecimal.ZERO.compareTo(ev.getRegularPrice()) >= 0) {
                errors.rejectValue("regularPrice", "error.regularprice");
            }
            if(ev.getVatPercentage() == null || BigDecimal.ZERO.compareTo(ev.getVatPercentage()) > 0) {
                errors.rejectValue("vat", "error.vat");
            }
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "currency", "error.currency");
        }
        if(ev.getAvailableSeats() < 1) {
            errors.rejectValue("availableSeats", "error.availableseats");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, Errors errors, String prefix) {
        if(StringUtils.isBlank(category.getName())) {
            errors.rejectValue(prefix + "name", "error.category.name");
        }
        if(category.isBounded() && category.getMaxTickets() < 1) {
            errors.rejectValue(prefix + "maxTickets", "error.category.maxtickets");
        }
        if(!category.getInception().isBefore(category.getExpiration())) {
            errors.rejectValue(prefix + "dateString", "error.date");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, Errors errors) {
        return validateCategory(category, errors, "");
    }

    private static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static ValidationResult evaluateValidationResult(Errors errors) {
        if (errors.hasFieldErrors()) {
            return ValidationResult.failed(errors.getFieldErrors()
                    .stream().map(ValidationResult.ValidationError::fromFieldError)
                    .collect(Collectors.toList()));
        }
        return ValidationResult.success();
    }

    public static ValidationResult validateTicketAssignment(UpdateTicketOwnerForm form, List<TicketFieldConfiguration> additionalFieldsForEvent, Optional<Errors> errorsOptional) {
        if(!errorsOptional.isPresent()) {
            return ValidationResult.success();//already validated
        }
        Errors errors = errorsOptional.get();
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "email", "error.email");
        String email = form.getEmail();
        if(!isEmailValid(email)) {
            errors.rejectValue("email", "error.email");
        }

        validateMaxLength(form.getFullName(), "fullName", "error.fullname", 255, errors);



        //
        for(TicketFieldConfiguration fieldConf : additionalFieldsForEvent) {

            boolean isField = form.getAdditional() !=null && form.getAdditional().containsKey(fieldConf.getName());

            if(!isField) {
                continue;
            }

            String formValue = form.getAdditional().get(fieldConf.getName());

            if(fieldConf.isMaxLengthDefined()) {
                validateMaxLength(formValue, "additional['"+fieldConf.getName()+"']", "error."+fieldConf.getName(), fieldConf.getMaxLength(), errors);
            }

            if(!fieldConf.getRestrictedValues().isEmpty()) {
                validateRestrictedValue(formValue, "additional['"+fieldConf.getName()+"']", "error."+fieldConf.getName(), fieldConf.getRestrictedValues(), errors);
            }

            //TODO: complete checks: min length, mandatory
        }

        return evaluateValidationResult(errors);
    }

    private static void validateRestrictedValue(String value, String fieldName, String errorCode, List<String> restrictedValues, Errors errors) {
        if(StringUtils.isNotBlank(value) && !restrictedValues.contains(value)) {
            errors.rejectValue(fieldName, errorCode);
        }
    }

    private static boolean isEmailValid(String email) {
        return StringUtils.isNotEmpty(email) && SIMPLE_E_MAIL_PATTERN.matcher(email).matches();
    }

    public static void validateMaxLength(String value, String fieldName, String errorCode, int maxLength, Errors errors) {
        if(StringUtils.isNotBlank(value) && StringUtils.length(value) > maxLength) {
            errors.rejectValue(fieldName, errorCode);
        }
    }

    public static ValidationResult validateWaitingQueueSubscription(WaitingQueueSubscriptionForm form, Errors errors) {
        if(!form.isTermAndConditionsAccepted()) {
            errors.rejectValue("termAndConditionsAccepted", "error.termAndConditionsAccepted");
        }
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "fullName", "error.fullName");
        if(!isEmailValid(form.getEmail())) {
            errors.rejectValue("email", "error.email");
        }
        if(form.getUserLanguage() == null) {
            errors.rejectValue("userLanguage", "error.userLanguage");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateAdditionalService(EventModification.AdditionalService additionalService, Errors errors) {
        return validateAdditionalService(additionalService, null, errors);
    }

    public static ValidationResult validateAdditionalService(EventModification.AdditionalService additionalService, EventModification eventModification, Errors errors) {
        if(additionalService.isFixPrice() && !Optional.ofNullable(additionalService.getPrice()).filter(p -> p.compareTo(BigDecimal.ZERO) >= 0).isPresent()) {
            errors.rejectValue("additionalServices", "error.price");
        }

        List<EventModification.AdditionalServiceText> descriptions = additionalService.getDescription();
        List<EventModification.AdditionalServiceText> titles = additionalService.getTitle();
        if(descriptions == null || titles == null || titles.size() != descriptions.size()) {
            errors.rejectValue("additionalServices", "error.title");
            errors.rejectValue("additionalServices", "error.description");
        } else {
            if(!validateDescriptionList(titles) || !containsAllRequiredTranslations(eventModification, titles)) {
                errors.rejectValue("additionalServices", "error.title");
            }
            if(!validateDescriptionList(descriptions) || !containsAllRequiredTranslations(eventModification, descriptions)) {
                errors.rejectValue("additionalServices", "error.description");
            }
        }

        DateTimeModification inception = additionalService.getInception();
        DateTimeModification expiration = additionalService.getExpiration();
        if(inception == null || expiration == null || expiration.isBefore(inception)) {
            errors.rejectValue("additionalServices", "error.inception");
            errors.rejectValue("additionalServices", "error.expiration");
        } else if(eventModification != null && expiration.isAfter(eventModification.getEnd())) {
            errors.rejectValue("additionalServices", "error.expiration");
        }

        return evaluateValidationResult(errors);

    }

    private static boolean containsAllRequiredTranslations(EventModification eventModification, List<EventModification.AdditionalServiceText> descriptions) {
        Optional<EventModification> optional = Optional.ofNullable(eventModification);
        return !optional.isPresent() ||
            optional.map(e -> ContentLanguage.findAllFor(e.getLocales()))
                .filter(l -> l.stream().allMatch(l1 -> descriptions.stream().anyMatch(d -> d.getLocale().equals(l1.getLanguage()))))
                .isPresent();
    }

    private static boolean validateDescriptionList(List<EventModification.AdditionalServiceText> descriptions) {
        return descriptions.stream().allMatch(t -> StringUtils.isNotBlank(t.getValue()));
    }
}
