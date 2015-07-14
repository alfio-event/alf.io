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
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketCategoryModification;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Validator {

    private static final Pattern SIMPLE_E_MAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9]+.*?@.+?\\..+$");

    private Validator() {
    }

    public static ValidationResult validateEventHeader(EventModification ev, Errors errors) {

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "shortName", "error.shortname");
        if(ev.getOrganizationId() < 0) {
            errors.rejectValue("organizationId", "error.organizationId");
        }
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "location", "error.location");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "description", "error.description");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "websiteUrl", "error.websiteurl");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "termsAndConditionsUrl", "error.termsandconditionsurl");

        if(ev.getFileBlobId() == null) {
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "imageUrl", "error.imageurl");
            if (!StringUtils.startsWith(ev.getImageUrl(), "https://")) {
                errors.rejectValue("imageUrl", "error.imageurl");
            }
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
            if(ev.getVat() == null || BigDecimal.ZERO.compareTo(ev.getVat()) > 0) {
                errors.rejectValue("vat", "error.vat");
            }
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "currency", "error.currency");
        }
        if(ev.getAvailableSeats() < 1) {
            errors.rejectValue("availableSeats", "error.availableseats");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateCategory(TicketCategoryModification category, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "error.category.name");
        if(category.isBounded() && category.getMaxTickets() < 1) {
            errors.rejectValue("maxTickets", "error.category.maxtickets");
        }
        if(!category.getInception().isBefore(category.getExpiration())) {
            errors.rejectValue("dateString", "error.date");
        }
        return evaluateValidationResult(errors);
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

    public static ValidationResult validateTicketAssignment(UpdateTicketOwnerForm form, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "email", "error.email");
        String email = form.getEmail();
        if(!isEmailValid(email)) {
            errors.rejectValue("email", "error.email");
        }

        validateMaxLength(form.getFullName(), "fullName", "error.fullname", 255, errors);
        validateMaxLength(form.getJobTitle(), "jobTitle", "error.jobtitle", 255, errors);
        validateMaxLength(form.getCompany(), "company", "error.company", 255, errors);
        validateMaxLength(form.getPhoneNumber(), "phoneNumber", "error.phonenumber", 255, errors);
        validateMaxLength(form.getCountry(), "country", "error.country", 255, errors);
        validateMaxLength(form.getTShirtSize(), "tShirtSize", "error.tshirtsize", 32, errors);
        validateMaxLength(form.getNotes(), "notes", "error.notes", 1024, errors);

        return evaluateValidationResult(errors);
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
}
