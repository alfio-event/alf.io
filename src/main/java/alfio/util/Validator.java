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

import alfio.controller.form.AdditionalFieldsContainer;
import alfio.controller.form.AdditionalServiceLinkForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.manager.ExtensionManager;
import alfio.manager.GroupManager;
import alfio.manager.SameCountryValidator;
import alfio.model.*;
import alfio.model.modification.AdditionalFieldRequest;
import alfio.model.modification.DateTimeModification;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isAnyBlank;

public final class Validator {

    // source: https://commons.apache.org/proper/commons-validator/apidocs/src-html/org/apache/commons/validator/routines/EmailValidator.html
    private static final Pattern SIMPLE_E_MAIL_PATTERN = Pattern.compile("^\\p{javaLetterOrDigit}[^\\s@]*@\\p{javaLetterOrDigit}[^\\s]*$");
    // this regex validates the e-mail to be a canonical address (i.e. test@example.org)
    private static final Pattern CANONICAL_MAIL_VALIDATOR = Pattern.compile("^\\p{javaLetterOrDigit}[^\\s@]*@\\p{javaLetterOrDigit}[^\\s@]*\\.\\p{javaAlphabetic}{2,}$");
    private static final Pattern FIELD_NAME_VALIDATOR = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9-_]*$");
    private static final String ERROR_DESCRIPTION = "error.description";
    private static final String EMAIL_KEY = "email";
    private static final String ERROR_EMAIL = "error.email";
    private static final String FIRST_NAME = "firstName";
    private static final String LAST_NAME = "lastName";
    private static final String FULL_NAME = "fullName";
    private static final String ADDITIONAL_PREFIX = "additional[";
    private static final String ADDITIONAL_SERVICES = "additionalServices";
    private static final String ERROR_RESTRICTED_VALUE = "error.restrictedValue";
    private static final Supplier<LocalDate> LOCAL_DATE_SUPPLIER = () -> LocalDate.now(ClockProvider.clock());
    private static final String DESCRIPTION = "description";
    private static final String TITLE = "title";

    private Validator() {
    }

    public static ValidationResult validateEventHeader(Optional<Event> event, EventModification ev,
                                                       int descriptionMaxLength,
                                                       Errors errors) {

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "shortName", "error.shortname");
        if(ev.getOrganizationId() < 0) {
            errors.rejectValue("organizationId", "error.organizationId");
        }

        if(allowsInPersonAccess(event, ev)) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "location", "error.location");
        }

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "websiteUrl", "error.websiteurl");

        validateUrl(ev.getWebsiteUrl(), errors, "websiteUrl", "error.websiteurl");
        validateUrl(ev.getTermsAndConditionsUrl(), errors, "websiteUrl", "error.restrictedValue");

        if (StringUtils.isNotBlank(ev.getPrivacyPolicyUrl())) {
            validateUrl(ev.getPrivacyPolicyUrl(), errors, "privacyPolicyUrl", "error.restrictedValue");
        }

        if(allowsInPersonAccess(event, ev) && isLocationMissing(ev)) {
            errors.rejectValue("locationDescriptor", "error.coordinates");
        }

        var descriptions = ev.getDescription();

        if(descriptions == null || descriptions.values().stream().anyMatch(v -> v == null || v.isBlank() || v.length() > descriptionMaxLength)) {
            errors.rejectValue(DESCRIPTION, ERROR_DESCRIPTION);
        }

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "termsAndConditionsUrl", "error.termsandconditionsurl");


        if(ev.getFileBlobId() == null) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "imageUrl", "error.imageurl");
            if (!StringUtils.startsWith(ev.getImageUrl(), "https://")) {
                errors.rejectValue("imageUrl", "error.imageurl");
            }
        }

        return evaluateValidationResult(errors);
    }

    static void validateUrl(String value, Errors errors, String propertyKey, String errorCode) {
        if (!(StringUtils.startsWith(value, "http://") || StringUtils.startsWith(value, "https://"))) {
            errors.rejectValue(propertyKey, errorCode);
        }
    }

    private static boolean isLocationMissing(EventModification em) {
        LocationDescriptor descriptor = em.getLocationDescriptor();
        return descriptor == null
            || isAnyBlank(descriptor.timeZone());
    }

    public static ValidationResult validateTicketCategories(EventModification ev, Errors errors) {
        if(CollectionUtils.isEmpty(ev.getTicketCategories())) {
            errors.rejectValue("ticketCategories", "error.ticketCategories");
        }
        if(ev.getTicketCategories().stream()
            .anyMatch(tc -> tc.getTicketAccessType() == TicketCategory.TicketAccessType.INHERIT && ev.getFormat() == Event.EventFormat.HYBRID)) {
            errors.rejectValue("ticketCategories", "error.ticketCategories.format");
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

    public static ValidationResult validateEventPrices(EventModification ev, Errors errors) {

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

    public static ValidationResult validateCategory(TicketCategoryModification category,
                                                    Errors errors,
                                                    String prefix,
                                                    EventModification eventModification,
                                                    int descriptionMaxLength) {
        return validateCategory(category, prefix, eventModification.getBegin().toLocalDateTime(), eventModification.getEnd().toLocalDateTime(), errors, descriptionMaxLength);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category,
                                                    String prefix,
                                                    LocalDateTime eventBegin,
                                                    LocalDateTime eventEnd,
                                                    Errors errors,
                                                    int descriptionMaxLength) {
        if(StringUtils.isBlank(category.getName())) {
            errors.rejectValue(prefix + "name", "error.category.name");
        }
        if(category.isBounded() && category.getMaxTickets() < 1) {
            errors.rejectValue(prefix + "maxTickets", "error.category.maxtickets");
        }
        if(!category.getInception().isBefore(category.getExpiration())) {
            errors.rejectValue(prefix + "dateString", "error.date");
        }
        if(isCategoryExpirationAfterEventEnd(category, eventEnd)) {
            errors.rejectValue(prefix + "expiration", "error.date.overflow");
        }
        if(isCategoryDescriptionTooLong(category, descriptionMaxLength)) {
            errors.rejectValue(prefix + DESCRIPTION, ERROR_DESCRIPTION);
        }

        // ticket validity dates
        var ticketValidityCheckStatus = checkDateRange(category::getTicketValidityStart, category::getTicketValidityEnd, eventBegin, eventEnd, true);
        if (ticketValidityCheckStatus != RangeValidationResultType.OK) {
            String propertyName = ticketValidityCheckStatus == RangeValidationResultType.FAILED_BEGIN ? "ticketValidityStart" : "ticketValidityEnd";
            errors.rejectValue(prefix + propertyName, "error.validity.date.overflow");
        }

        // check-in dates
        var checkInDateCheckStatus = checkDateRange(category::getValidCheckInFrom, category::getValidCheckInTo, eventBegin, eventEnd, false);
        if (checkInDateCheckStatus != RangeValidationResultType.OK) {
            String propertyName = checkInDateCheckStatus == RangeValidationResultType.FAILED_BEGIN ? "validCheckInFrom" : "validCheckInTo";
            errors.rejectValue(prefix + propertyName, "error.checkin.date.overflow");
        }

        return evaluateValidationResult(errors);
    }

    enum RangeValidationResultType {
        OK,
        FAILED_BEGIN,
        FAILED_END
    }

    static RangeValidationResultType checkDateRange(
        Supplier<DateTimeModification> dateFromSupplier,
        Supplier<DateTimeModification> dateToSupplier,
        LocalDateTime eventBegin,
        LocalDateTime eventEnd,
        boolean strictBegin) {
        var start = toLocalDateTimeOrElse(dateFromSupplier.get(), eventBegin);
        var end = toLocalDateTimeOrElse(dateToSupplier.get(), eventEnd);

        if (start.isAfter(end) || (strictBegin && start.isBefore(eventBegin))) {
            return RangeValidationResultType.FAILED_BEGIN;
        } else if (!end.isAfter(eventBegin) || end.isAfter(eventEnd)) {
            return RangeValidationResultType.FAILED_END;
        }
        return RangeValidationResultType.OK;
    }

    private static LocalDateTime toLocalDateTimeOrElse(DateTimeModification input, LocalDateTime defaultValue) {
        if (input != null) {
            return input.toLocalDateTime();
        }
        return Objects.requireNonNull(defaultValue);
    }

    private static boolean isCategoryDescriptionTooLong(TicketCategoryModification category, int descriptionMaxLength) {
        return category.getDescription() != null
            && !category.getDescription().isEmpty()
            && category.getDescription().values().stream()
                .filter(Objects::nonNull)
                .anyMatch(v -> v.length() > descriptionMaxLength);
    }

    private static boolean isCategoryExpirationAfterEventEnd(TicketCategoryModification category, LocalDateTime eventEnd) {
        return category.getExpiration().toLocalDateTime().isAfter(eventEnd);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, LocalDateTime eventBegin, LocalDateTime eventEnd, Errors errors, int descriptionMaxLength) {
        return validateCategory(category, "", eventBegin, eventEnd, errors, descriptionMaxLength);
    }

    private static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    public static ValidationResult evaluateValidationResult(Errors errors) {
        if (errors.hasFieldErrors()) {
            return ValidationResult.failed(errors.getFieldErrors()
                    .stream().map(ValidationResult.ErrorDescriptor::fromFieldError)
                    .toList());
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

    public static class AdditionalFieldsFilterer {

        private final List<PurchaseContextFieldConfiguration> purchaseContextFields;
        private final List<Ticket> ticketsInReservation;
        private final Set<Integer> additionalFieldsForReservation;
        private final Optional<Ticket> firstTicketInReservation;
        private final boolean eventSupportsAdditionalFieldsLink;
        private final List<AdditionalServiceItem> additionalServiceItems;

        public AdditionalFieldsFilterer(List<PurchaseContextFieldConfiguration> purchaseContextFields,
                                        List<Ticket> ticketsInReservation,
                                        boolean eventSupportsAdditionalFieldsLink,
                                        List<AdditionalServiceItem> additionalServiceItems) {
            this.purchaseContextFields = purchaseContextFields;
            this.ticketsInReservation = ticketsInReservation;
            this.additionalFieldsForReservation = additionalServiceItems.stream().map(AdditionalServiceItem::getAdditionalServiceId).collect(Collectors.toSet());
            this.firstTicketInReservation = ticketsInReservation.stream().findFirst();
            this.eventSupportsAdditionalFieldsLink = eventSupportsAdditionalFieldsLink;
            this.additionalServiceItems = additionalServiceItems;
        }


        public List<PurchaseContextFieldConfiguration> getFieldsForTicket(UUID publicUuid, Set<PurchaseContextFieldConfiguration.Context> requestedContexts) {
            var ticketOptional = ticketsInReservation.stream().filter(t -> t.getPublicUuid().equals(publicUuid)).findFirst();
            if (ticketOptional.isEmpty()) {
                return List.of();
            }
            var ticket = ticketOptional.get();
            var isFirstTicket = firstTicketInReservation.map(first -> ticket.getId() == first.getId()).orElse(false);
            return filterFieldsForTicket(purchaseContextFields, ticket, additionalFieldsForReservation, isFirstTicket, eventSupportsAdditionalFieldsLink, additionalServiceItems, requestedContexts);
        }

        public List<PurchaseContextFieldConfiguration> getFieldsForSubscription() {
            return purchaseContextFields;
        }

        private static List<PurchaseContextFieldConfiguration> filterFieldsForTicket(List<PurchaseContextFieldConfiguration> additionalFieldsForEvent,
                                                                                     Ticket ticket,
                                                                                     Set<Integer> additionalServiceIds,
                                                                                     boolean isFirstTicket,
                                                                                     boolean eventSupportsAdditionalFieldsLink,
                                                                                     List<AdditionalServiceItem> additionalServiceItems,
                                                                                     Set<PurchaseContextFieldConfiguration.Context> requestedContexts) {
            return additionalFieldsForEvent.stream()
                .filter(field -> field.rulesApply(ticket.getCategoryId()))
                .filter(f -> {
                    if (!requestedContexts.contains(f.getContext())) {
                        return false;
                    }
                    if (f.getContext() == PurchaseContextFieldConfiguration.Context.ATTENDEE) {
                        return true;
                    }
                    // field context is "Additional Service"
                    if (!isFirstTicket && !eventSupportsAdditionalFieldsLink) {
                        return false;
                    }
                    boolean included = isAdditionalServiceIncluded(f, additionalServiceIds);
                    return included && (!eventSupportsAdditionalFieldsLink || checkLinked(ticket, additionalServiceItems, f));
                }).toList();
        }

        private static boolean checkLinked(Ticket ticket, List<AdditionalServiceItem> additionalServiceItems, PurchaseContextFieldConfiguration tfc) {
            return additionalServiceItems.stream()
                .anyMatch(asi -> tfc.getAdditionalServiceId() == asi.getAdditionalServiceId() && Objects.equals(ticket.getId(), asi.getTicketId()));
        }

        private static boolean isAdditionalServiceIncluded(PurchaseContextFieldConfiguration f, Set<Integer> additionalServiceIds) {
            if (f.getAdditionalServiceId() == null) {
                return false;
            }
            return additionalServiceIds.contains(f.getAdditionalServiceId());
        }
    }


    public static ValidationResult validateTicketAssignment(UpdateTicketOwnerForm form,
                                                            List<PurchaseContextFieldConfiguration> additionalFieldsForTicket,
                                                            Optional<BindingResult> errorsOptional,
                                                            Event event,
                                                            String baseField,
                                                            SameCountryValidator vatValidator,
                                                            ExtensionManager extensionManager) {
        if(errorsOptional.isEmpty()) {
            return ValidationResult.success();//already validated
        }

        String prefix = StringUtils.trimToEmpty(baseField);

        if(!prefix.isEmpty() && !prefix.endsWith(".")) {
            prefix = prefix + ".";
        }

        var errors = errorsOptional.get();
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + EMAIL_KEY, ERROR_EMAIL);
        String email = form.getEmail();
        if(!isEmailValid(email)) {
            errors.rejectValue(prefix + EMAIL_KEY, ERROR_EMAIL);
        }

        if(event.mustUseFirstAndLastName()) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + FIRST_NAME, ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
            validateMaxLength(form.getFirstName(), prefix + FIRST_NAME, ErrorsCode.STEP_2_MAX_LENGTH_FIRSTNAME, 255, errors);

            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + LAST_NAME, ErrorsCode.STEP_2_EMPTY_LASTNAME);
            validateMaxLength(form.getLastName(), prefix + LAST_NAME, ErrorsCode.STEP_2_MAX_LENGTH_LASTNAME, 255, errors);
        } else {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, prefix + FULL_NAME, ErrorsCode.STEP_2_EMPTY_FULLNAME);
            validateMaxLength(form.getFullName(), prefix + FULL_NAME, ErrorsCode.STEP_2_MAX_LENGTH_FULLNAME, 255, errors);
        }


        //
        final String prefixForLambda = prefix;
        for(PurchaseContextFieldConfiguration fieldConf : additionalFieldsForTicket) {
            validateFieldConfiguration(form, vatValidator, errors, prefixForLambda, fieldConf);
        }

        if (!errors.hasErrors()) {
            extensionManager.handleTicketUpdateValidation(event, form, errors, prefix);
        }

        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateAdditionalItemFieldsForTicket(AdditionalServiceLinkForm form,
                                                                         List<PurchaseContextFieldConfiguration> additionalFieldsForTicket,
                                                                         BindingResult errors,
                                                                         String prefix,
                                                                         SameCountryValidator vatValidator,
                                                                         List<AdditionalServiceLinkForm> allForms,
                                                                         List<AdditionalServiceItem> additionalServiceItems) {
        // ticket may be linked to different additional items, each one requiring fields
        var allKeys = allForms.stream().flatMap(f -> f.getAdditional().keySet().stream()).collect(Collectors.toSet());
        var foundKeys = additionalFieldsForTicket.stream()
            .map(PurchaseContextFieldConfiguration::getName)
            .filter(allKeys::contains)
            .collect(Collectors.toSet());
        for (PurchaseContextFieldConfiguration fieldConf : additionalFieldsForTicket) {
            validateFieldConfiguration(form, vatValidator, errors, prefix + ".", fieldConf, foundKeys.contains(fieldConf.getName()));
        }
        return evaluateValidationResult(errors);
    }

    public static void validateFieldConfiguration(AdditionalFieldsContainer form,
                                                  SameCountryValidator vatValidator,
                                                  Errors errors,
                                                  String prefixForLambda,
                                                  PurchaseContextFieldConfiguration fieldConf) {
        validateFieldConfiguration(form, vatValidator, errors, prefixForLambda, fieldConf, false);
    }

    private static void validateFieldConfiguration(AdditionalFieldsContainer form,
                                                   SameCountryValidator vatValidator,
                                                   Errors errors,
                                                   String prefixForLambda,
                                                   PurchaseContextFieldConfiguration fieldConf,
                                                   boolean skipPresenceCheck) {
        boolean isFieldPresent = form.getAdditional() != null && form.getAdditional().containsKey(fieldConf.getName());

        if(!skipPresenceCheck && !isFieldPresent) {
            if (fieldConf.isRequired()) { // sometimes the field is not propagated, so, if it's required, we need to do some additional work
                if (form.getAdditional() == null) {
                    form.setAdditional(new HashMap<>());
                }
                form.getAdditional().put(fieldConf.getName(), Collections.singletonList(""));
                errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"][0]", ErrorsCode.EMPTY_FIELD);
            }
            return;
        }
        var values = form.getAdditional().computeIfAbsent(fieldConf.getName(), k -> List.of());

        //handle required for multiple choice (checkbox) where required is interpreted as at least one!
        if (fieldConf.isRequired() && fieldConf.getCount() > 1  && values.stream().allMatch(StringUtils::isBlank)) {
            errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]", ErrorsCode.EMPTY_FIELD);
        }

        for(int i = 0; i < values.size(); i++) {
            validateFieldValue(vatValidator, errors, prefixForLambda, fieldConf, values, i);
        }
    }

    private static void validateFieldValue(SameCountryValidator vatValidator, Errors errors, String prefixForLambda, PurchaseContextFieldConfiguration fieldConf, List<String> values, int i) {
        String formValue = values.get(i);

        fieldValueBasicValidation(errors, prefixForLambda, fieldConf, i, formValue);

        try {
            if (fieldConf.isEuVat() && !vatValidator.test(formValue)) {
                errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName() + "]["+ i +"]", ErrorsCode.STEP_2_INVALID_VAT);
            }
        } catch (IllegalStateException e) {
            errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName() + "]["+ i +"]", ErrorsCode.VIES_IS_DOWN);
        }
    }

    private static void fieldValueBasicValidation(Errors errors, String prefixForLambda, PurchaseContextFieldConfiguration fieldConf, int i, String formValue) {

        validateLength(errors, prefixForLambda, fieldConf, i, formValue);

        if(!fieldConf.getRestrictedValues().isEmpty()) {
            validateRestrictedValue(formValue, prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ i +"]",
                ERROR_RESTRICTED_VALUE, fieldConf.getRestrictedValues(), errors);
        }

        if(fieldConf.isRequired() && fieldConf.getCount() == 1 && StringUtils.isBlank(formValue)){
            errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ i +"]", ErrorsCode.EMPTY_FIELD);
        }

        if(fieldConf.hasDisabledValues() && fieldConf.getDisabledValues().contains(formValue)) {
            errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ i +"]",
                "error.disabledValue", null, null);
        }

        if (!errors.hasFieldErrors() && StringUtils.isNotBlank(formValue) && fieldConf.isDateOfBirth()) {
            int age = calculateAge(formValue, true, LOCAL_DATE_SUPPLIER);
            if (age < 0) {
                // age was not provided in the right format
                errors.rejectValue(prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ i +"]", ErrorsCode.EMPTY_FIELD);
            }
        }
    }

    private static void validateLength(Errors errors,
                                       String prefixForLambda,
                                       PurchaseContextFieldConfiguration fieldConf,
                                       int fieldIndex,
                                       String formValue) {

        boolean isDateOfBirth = fieldConf.isDateOfBirth();

        if (isDateOfBirth) {
            // we require date of birth to be in the past
            validateDateInThePast(formValue, prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ fieldIndex +"]", errors);
        }

        if(fieldConf.isMaxLengthDefined()) {
            validateMaxLength(formValue, prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ fieldIndex +"]", isDateOfBirth ? "error.tooOld" : "error.tooLong", fieldConf, errors);
        }

        if(StringUtils.isNotBlank(formValue) && fieldConf.isMinLengthDefined()) {
            validateMinLength(formValue, prefixForLambda + ADDITIONAL_PREFIX + fieldConf.getName()+"]["+ fieldIndex +"]", isDateOfBirth ? "error.tooYoung" : "error.tooShort", fieldConf, errors);
        }
    }

    private static void validateRestrictedValue(String value, String fieldName, String errorCode, List<String> restrictedValues, Errors errors) {
        if(StringUtils.isNotBlank(value) && !restrictedValues.contains(value)) {
            errors.rejectValue(fieldName, errorCode);
        }
    }

    private static boolean allowsInPersonAccess(Optional<Event> event, EventModification ev) {
        return event.map(Event::getFormat).orElse(ev.getFormat()) != Event.EventFormat.ONLINE;
    }

    public static boolean isEmailValid(String email) {
        return StringUtils.isNotEmpty(email) && !email.strip().endsWith(".") && SIMPLE_E_MAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isCanonicalMailAddress(String email) {
        return StringUtils.isNotEmpty(email) && CANONICAL_MAIL_VALIDATOR.matcher(email).matches();
    }

    private static void validateMinLength(String value, String fieldName, String errorCode, PurchaseContextFieldConfiguration fieldConfiguration, Errors errors) {
        if (fieldConfiguration.isDateOfBirth()) {
            validateMinAge(value, fieldName, errorCode, fieldConfiguration, errors);
        } else if (StringUtils.length(value) < fieldConfiguration.getMinLength()) {
            errors.rejectValue(fieldName, errorCode, new Object[] { fieldConfiguration.getMinLength() }, null);
        }
    }

    static void validateMinAge(String value,
                               String fieldName,
                               String errorCode,
                               PurchaseContextFieldConfiguration fieldConfiguration,
                               Errors errors) {
        validateMinAge(value, fieldName, errorCode, fieldConfiguration, errors, LOCAL_DATE_SUPPLIER);
    }

    static void validateMinAge(String value,
                               String fieldName,
                               String errorCode,
                               PurchaseContextFieldConfiguration fieldConfiguration,
                               Errors errors,
                               Supplier<LocalDate> currentDateSupplier) {
        int minAge = fieldConfiguration.getMinLength();
        int age = calculateAge(value, false, currentDateSupplier);
        if (age >= 0 && age < minAge) {
            errors.rejectValue(fieldName, errorCode, new Object[] { minAge }, null);
        }
    }

    static void validateDateInThePast(String value, String fieldName, Errors errors) {
        if (StringUtils.isBlank(value)) {
            // value is not present. We cannot check it.
            return;
        }
        try {
            var period = Period.between(LocalDate.parse(value), LocalDate.now(ClockProvider.clock()));
            if (period.isNegative() || period.isZero()) {
                errors.rejectValue(fieldName, ERROR_RESTRICTED_VALUE, null, null);
            }
        } catch(DateTimeException dex) {
            errors.rejectValue(fieldName, ERROR_RESTRICTED_VALUE, null, null);
        }
    }

    private static void validateMaxLength(String value, String fieldName, String errorCode, PurchaseContextFieldConfiguration fieldConfiguration, Errors errors) {
        if (fieldConfiguration.isDateOfBirth()) {
            validateMaxAge(value, fieldName, errorCode, fieldConfiguration, errors);
        } else {
            validateMaxLength(value, fieldName, errorCode, fieldConfiguration.getMaxLength(), errors);
        }
    }

    static void validateMaxAge(String value, String fieldName, String errorCode, PurchaseContextFieldConfiguration fieldConfiguration, Errors errors) {
        validateMaxAge(value, fieldName, errorCode, fieldConfiguration, errors, LOCAL_DATE_SUPPLIER);
    }

    static void validateMaxAge(String value,
                               String fieldName,
                               String errorCode,
                               PurchaseContextFieldConfiguration fieldConfiguration,
                               Errors errors,
                               Supplier<LocalDate> localDateSupplier) {
        int maxAge = fieldConfiguration.getMaxLength();
        int age = calculateAge(value, true, localDateSupplier);
        if (age > maxAge) {
            errors.rejectValue(fieldName, errorCode, new Object[] { maxAge }, null);
        }
    }

    private static int calculateAge(String value, boolean addRemainder, Supplier<LocalDate> currentDateSupplier) {
        try {
            var period = Period.between(LocalDate.parse(value), currentDateSupplier.get());
            int years = period.getYears();
            if (addRemainder && (period.getMonths() > 0 || period.getDays() > 0)) {
                years += 1;
            }
            return years;
        } catch (DateTimeException ex) {
            return -1;
        }
    }

    private static void validateMaxLength(String value, String fieldName, String errorCode, int maxLength, Errors errors) {
        if(StringUtils.isNotBlank(value) && StringUtils.length(value) > maxLength) {
            errors.rejectValue(fieldName, errorCode, new Object[] { maxLength }, null);
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
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, FIRST_NAME, "error.firstname");
            validateMaxLength(form.getFirstName(), FIRST_NAME, "error.firstname", 255, errors);

            ValidationUtils.rejectIfEmptyOrWhitespace(errors, LAST_NAME, "lastname.fullname");
            validateMaxLength(form.getLastName(), LAST_NAME, "error.lastname", 255, errors);
        } else {
            validateMaxLength(form.getFullName(), FULL_NAME, "error.fullname", 255, errors);
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, FULL_NAME, "error.fullname");
        }

        if(!isEmailValid(form.getEmail())) {
            errors.rejectValue(EMAIL_KEY, ERROR_EMAIL);
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
        boolean eventModificationIsNotNull = eventModification != null;
        if(additionalService.isFixPrice() && Optional.ofNullable(additionalService.getPrice()).filter(p -> p.compareTo(BigDecimal.ZERO) >= 0).isEmpty()) {
            rejectFieldForAdditionalService("price", eventModificationIsNotNull, errors);
        }

        List<EventModification.AdditionalServiceText> descriptions = additionalService.getDescription();
        List<EventModification.AdditionalServiceText> titles = additionalService.getTitle();
        if(descriptions == null || titles == null || titles.size() != descriptions.size()) {
            rejectFieldForAdditionalService(TITLE, eventModificationIsNotNull, errors);
            rejectFieldForAdditionalService(DESCRIPTION, eventModificationIsNotNull, errors);
        } else {
            if(!validateDescriptionList(titles) || !containsAllRequiredTranslations(eventModification, titles)) {
                rejectFieldForAdditionalService(TITLE, eventModificationIsNotNull, errors);
            }
            if(!validateDescriptionList(descriptions) || !containsAllRequiredTranslations(eventModification, descriptions)) {
                rejectFieldForAdditionalService(DESCRIPTION, eventModificationIsNotNull, errors);
            }
        }

        DateTimeModification inception = additionalService.getInception();
        DateTimeModification expiration = additionalService.getExpiration();
        if(inception == null || expiration == null || expiration.isBefore(inception)) {
            rejectFieldForAdditionalService("inception", eventModificationIsNotNull, errors);
            rejectFieldForAdditionalService("expiration", eventModificationIsNotNull, errors);
        } else if(eventModification != null && expiration.isAfter(eventModification.getEnd())) {
            rejectFieldForAdditionalService("expiration", true, errors);
        }

        return evaluateValidationResult(errors);

    }

    private static void rejectFieldForAdditionalService(String field, boolean eventModification, Errors errors) {
        var errorField = eventModification ? ADDITIONAL_SERVICES : field;
        errors.rejectValue(errorField, "error."+field);
    }

    private static boolean containsAllRequiredTranslations(EventModification eventModification, List<EventModification.AdditionalServiceText> descriptions) {
        Optional<EventModification> optional = Optional.ofNullable(eventModification);
        return optional.isEmpty() ||
            optional.map(e -> ContentLanguage.findAllFor(e.getLocales()))
                .filter(l -> l.stream().allMatch(l1 -> descriptions.stream().anyMatch(d -> d.getLocale().equals(l1.getLanguage()))))
                .isPresent();
    }

    private static boolean validateDescriptionList(List<EventModification.AdditionalServiceText> descriptions) {
        return descriptions.stream().allMatch(t -> StringUtils.isNotBlank(t.getValue()));
    }

    public static ValidationResult validateAdditionalFields(List<PurchaseContextFieldConfiguration> fieldConf, AdditionalFieldRequest field, Errors errors){
        String duplicateName = fieldConf.stream().map(PurchaseContextFieldConfiguration::getName).filter(name -> name.equalsIgnoreCase(field.getName())).findAny().orElse("");
        if(StringUtils.isNotBlank(duplicateName)){
            errors.rejectValue("name", ErrorCode.DUPLICATE);
        } else if (!validateAdditionalInfoName(field.getName())) {
            errors.rejectValue("name", "pattern");
        }
        return evaluateValidationResult(errors);
    }

    public static boolean validateAdditionalInfoName(String additionalInfoName) {
        return FIELD_NAME_VALIDATOR.matcher(additionalInfoName).matches();
    }


    public static class AdvancedTicketAssignmentValidator implements Function<AdvancedValidationContext, Result<Void>> {

        private final SameCountryValidator vatValidator;
        private final GroupManager.WhitelistValidator whitelistValidator;

        public AdvancedTicketAssignmentValidator(SameCountryValidator vatValidator, GroupManager.WhitelistValidator whitelistValidator) {
            this.vatValidator = vatValidator;
            this.whitelistValidator = whitelistValidator;
        }


        @Override
        public Result<Void> apply(AdvancedValidationContext context) {

            Optional<PurchaseContextFieldConfiguration> vatField = context.purchaseContextFieldConfigurations.stream()
                .filter(PurchaseContextFieldConfiguration::isEuVat)
                .filter(f -> context.updateTicketOwnerForm.getAdditional() !=null && context.updateTicketOwnerForm.getAdditional().containsKey(f.getName()))
                .findFirst();

            Optional<String> vatNr = vatField.map(c -> Objects.requireNonNull(context.updateTicketOwnerForm.getAdditional()).get(c.getName()).get(0));
            String vatFieldName = vatField.map(PurchaseContextFieldConfiguration::getName).orElse("");

            return new Result.Builder<Void>()
                .checkPrecondition(() -> vatNr.isEmpty() || vatValidator.test(vatNr.get()), ErrorCode.custom(ErrorsCode.STEP_2_INVALID_VAT, "additional['"+vatFieldName+"']"))
                .checkPrecondition(() -> whitelistValidator.test(new GroupManager.WhitelistValidationItem(context.categoryId, context.updateTicketOwnerForm.getEmail())), ErrorCode.custom(ErrorsCode.STEP_2_WHITELIST_CHECK_FAILED, EMAIL_KEY))
                .build(() -> null);
        }
    }


    public static class AdvancedValidationContext {
        private final UpdateTicketOwnerForm updateTicketOwnerForm;
        private final List<PurchaseContextFieldConfiguration> purchaseContextFieldConfigurations;
        private final int categoryId;
        private final String ticketUuid;
        private final String prefix;

        public AdvancedValidationContext(UpdateTicketOwnerForm updateTicketOwnerForm, List<PurchaseContextFieldConfiguration> purchaseContextFieldConfigurations, int categoryId, String ticketUuid, String prefix) {
            this.updateTicketOwnerForm = updateTicketOwnerForm;
            this.purchaseContextFieldConfigurations = purchaseContextFieldConfigurations;
            this.categoryId = categoryId;
            this.ticketUuid = ticketUuid;
            this.prefix = prefix;
        }
    }

}
