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
import alfio.manager.EuVatChecker;
import alfio.manager.GroupManager;
import alfio.model.ContentLanguage;
import alfio.model.Event;
import alfio.model.TicketFieldConfiguration;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;

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

        if(isInternal(event, ev) && isLocationMissing(ev)) {
            errors.rejectValue("locationDescriptor", "error.coordinates");
        }

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

    private static boolean isLocationMissing(EventModification em) {
        LocationDescriptor descriptor = em.getLocationDescriptor();
        return descriptor == null
            || isAnyBlank(descriptor.getLatitude(), descriptor.getLongitude(), descriptor.getTimeZone());
    }

    public static ValidationResult validateTicketCategories(EventModification ev, Errors errors) {
        if(CollectionUtils.isEmpty(ev.getTicketCategories())) {
            errors.rejectValue("ticketCategories", "error.ticketCategories");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateEventDates(EventModification ev, Errors errors) {
        if(ev.getBegin() == null || ev.getBegin().getDate() == null || ev.getBegin().getTime() == null) {
            errors.rejectValue("begin", "error.beginDate");
        }
        if(ev.getEnd() == null || ev.getEnd().getDate() == null || ev.getEnd().getTime() == null) {
            errors.rejectValue("end", "error.endDate");
        }
        if(!errors.hasErrors() && !ev.getEnd().isAfter(ev.getBegin())) {
            errors.rejectValue("end", "error.endDate");
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
                errors.rejectValue("vatPercentage", "error.vat");
            }
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "currency", "error.currency");
        }
        if(ev.getAvailableSeats() == null || ev.getAvailableSeats() < 1) {
            errors.rejectValue("availableSeats", "error.availableseats");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, Errors errors, String prefix, EventModification eventModification) {
        if(StringUtils.isBlank(category.getName())) {
            errors.rejectValue(prefix + "name", "error.category.name");
        }
        if(category.isBounded() && category.getMaxTickets() < 1) {
            errors.rejectValue(prefix + "maxTickets", "error.category.maxtickets");
        }
        if(!category.getInception().isBefore(category.getExpiration())) {
            errors.rejectValue(prefix + "dateString", "error.date");
        }
        if(eventModification != null && isCategoryExpirationAfterEventEnd(category, eventModification)) {
            errors.rejectValue(prefix + "expiration", "error.date.overflow");
        }
        return evaluateValidationResult(errors);
    }

    private static boolean isCategoryExpirationAfterEventEnd(TicketCategoryModification category, EventModification eventModification) {
        return eventModification.getEnd() == null
            || eventModification.getEnd().getDate() == null
            || category.getExpiration().isAfter(eventModification.getEnd());
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, Errors errors) {
        return validateCategory(category, errors, "", null);
    }

    private static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static ValidationResult evaluateValidationResult(Errors errors) {
        if (errors.hasFieldErrors()) {
            return ValidationResult.failed(errors.getFieldErrors()
                    .stream().map(ValidationResult.ErrorDescriptor::fromFieldError)
                    .collect(Collectors.toList()));
        }
        return ValidationResult.success();
    }

    public static ValidationResult performAdvancedValidation(AdvancedTicketAssignmentValidator advancedValidator, AdvancedValidationContext context, Errors errors) {
        if(errors == null) {
            return ValidationResult.success();
        }
        Result<Void> advancedValidation = advancedValidator.apply(context);
        if(!advancedValidation.isSuccess()) {
            ErrorCode error = advancedValidation.getFirstErrorOrNull();
            Validate.notNull(error, "unexpected error");
            errors.rejectValue(StringUtils.defaultString(context.prefix) + error.getDescription(), error.getCode());
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateTicketAssignment(UpdateTicketOwnerForm form,
                                                            List<TicketFieldConfiguration> additionalFieldsForEvent,
                                                            Optional<Errors> errorsOptional,
                                                            Event event,
                                                            String baseField,
                                                            EuVatChecker.SameCountryValidator vatValidator) {
        if(!errorsOptional.isPresent()) {
            return ValidationResult.success();//already validated
        }

        String prefix = StringUtils.trimToEmpty(baseField);

        if(!prefix.isEmpty() && !prefix.endsWith(".")) {
            prefix = prefix + ".";
        }

        Errors errors = errorsOptional.get();
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + "email", "error.email");
        String email = form.getEmail();
        if(!isEmailValid(email)) {
            errors.rejectValue(prefix + "email", "error.email");
        }

        if(event.mustUseFirstAndLastName()) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + "firstName", ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
            validateMaxLength(form.getFirstName(), prefix + "firstName", ErrorsCode.STEP_2_MAX_LENGTH_FIRSTNAME, 255, errors);

            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + "lastName", ErrorsCode.STEP_2_EMPTY_LASTNAME);
            validateMaxLength(form.getLastName(), prefix + "lastName", ErrorsCode.STEP_2_MAX_LENGTH_LASTNAME, 255, errors);
        } else {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + "fullName", ErrorsCode.STEP_2_EMPTY_FULLNAME);
            validateMaxLength(form.getFullName(), prefix + "fullName", ErrorsCode.STEP_2_MAX_LENGTH_FULLNAME, 255, errors);
        }


        //
        final String prefixForLambda = prefix;
        for(TicketFieldConfiguration fieldConf : additionalFieldsForEvent) {

            boolean isField = form.getAdditional() !=null && form.getAdditional().containsKey(fieldConf.getName());

            if(!isField) {
                continue;
            }
            
            List<String> values = Optional.ofNullable(form.getAdditional().get(fieldConf.getName())).orElse(Collections.emptyList());
            for(int i = 0; i < values.size(); i++) {
                String formValue = values.get(i);
                if(fieldConf.isMaxLengthDefined()) {
                    validateMaxLength(formValue, prefixForLambda + "additional["+fieldConf.getName()+"]["+i+"]", "error."+fieldConf.getName(), fieldConf.getMaxLength(), errors);
                }

                if(StringUtils.isNotBlank(formValue) && fieldConf.isMinLengthDefined() && StringUtils.length(formValue) < fieldConf.getMinLength()) {
                    errors.rejectValue(prefixForLambda + "additional["+fieldConf.getName()+"]["+i+"]", "error."+fieldConf.getName());
                }

                if(!fieldConf.getRestrictedValues().isEmpty()) {
                    validateRestrictedValue(formValue, prefixForLambda + "additional["+fieldConf.getName()+"]["+i+"]", "error."+fieldConf.getName(), fieldConf.getRestrictedValues(), errors);
                }

                if(fieldConf.isRequired() && StringUtils.isBlank(formValue)){
                    errors.rejectValue(prefixForLambda + "additional["+fieldConf.getName()+"]["+i+"]", "error."+fieldConf.getName());
                }

                if(fieldConf.hasDisabledValues() && fieldConf.getDisabledValues().contains(formValue)) {
                    errors.rejectValue(prefixForLambda + "additional["+fieldConf.getName()+"]["+i+"]", "error."+fieldConf.getName());
                }

                try {
                    if (fieldConf.isEuVat() && !vatValidator.test(formValue)) {
                        errors.rejectValue(prefixForLambda + "additional[" + fieldConf.getName() + "]["+i+"]", ErrorsCode.STEP_2_INVALID_VAT);
                    }
                } catch (IllegalStateException e) {
                    errors.rejectValue(prefixForLambda + "additional[" + fieldConf.getName() + "]["+i+"]", ErrorsCode.VIES_IS_DOWN);
                }
            }


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

    public static ValidationResult validateWaitingQueueSubscription(WaitingQueueSubscriptionForm form, Errors errors, Event event) {
        if(!form.isTermAndConditionsAccepted()) {
            errors.rejectValue("termAndConditionsAccepted", ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }

        if(StringUtils.isNotEmpty(event.getPrivacyPolicyUrl()) && !form.isPrivacyPolicyAccepted()) {
            errors.rejectValue("privacyPolicyAccepted", ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }

        if(event.mustUseFirstAndLastName()) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "firstName", "error.firstname");
            validateMaxLength(form.getFirstName(), "firstName", "error.firstname", 255, errors);

            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "lastName", "lastname.fullname");
            validateMaxLength(form.getLastName(), "lastName", "error.lastname", 255, errors);
        } else {
            validateMaxLength(form.getFullName(), "fullName", "error.fullname", 255, errors);
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "fullName", "error.fullname");
        }

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

    public static ValidationResult validateAdditionalFields(List<TicketFieldConfiguration> fieldConf, EventModification.AdditionalField field, Errors errors){
        String duplicateName = fieldConf.stream().filter(f->f.getName().equalsIgnoreCase(field.getName())).map(TicketFieldConfiguration::getName).findAny().orElse("");
        if(StringUtils.isNotBlank(duplicateName)){
            errors.rejectValue("name", ErrorCode.DUPLICATE);
        }
        return evaluateValidationResult(errors);
    }

    @RequiredArgsConstructor
    public static class AdvancedTicketAssignmentValidator implements Function<AdvancedValidationContext, Result<Void>> {

        private final EuVatChecker.SameCountryValidator vatValidator;
        private final GroupManager.WhitelistValidator whitelistValidator;


        @Override
        public Result<Void> apply(AdvancedValidationContext context) {

            Optional<TicketFieldConfiguration> vatField = context.ticketFieldConfigurations.stream()
                .filter(TicketFieldConfiguration::isEuVat)
                .filter(f -> context.updateTicketOwnerForm.getAdditional() !=null && context.updateTicketOwnerForm.getAdditional().containsKey(f.getName()))
                .findFirst();

            Optional<String> vatNr = vatField.map(c -> context.updateTicketOwnerForm.getAdditional().get(c.getName()).get(0));
            String vatFieldName = vatField.map(TicketFieldConfiguration::getName).orElse("");

            return new Result.Builder<Void>()
                .checkPrecondition(() -> !vatNr.isPresent() || vatValidator.test(vatNr.get()), ErrorCode.custom(ErrorsCode.STEP_2_INVALID_VAT, "additional['"+vatFieldName+"']"))
                .checkPrecondition(() -> whitelistValidator.test(new GroupManager.WhitelistValidationItem(context.categoryId, context.updateTicketOwnerForm.getEmail())), ErrorCode.custom(ErrorsCode.STEP_2_WHITELIST_CHECK_FAILED, "email"))
                .build(() -> null);
        }
    }

    @RequiredArgsConstructor
    public static class AdvancedValidationContext {
        private final UpdateTicketOwnerForm updateTicketOwnerForm;
        private final List<TicketFieldConfiguration> ticketFieldConfigurations;
        private final int categoryId;
        private final String ticketUuid;
        private final String prefix;
    }

}
