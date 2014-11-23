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

import alfio.model.modification.EventModification;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Validator {

    private Validator() {
    }

    public static ValidationResult validateEventHeader(EventModification ev, Errors errors) {

        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "shortName", "error.shortname");
        if(ev.getOrganizationId() < 0) {
            errors.reject("organizationId", "error.organizationId");
        }
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "location", "error.location");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "description", "error.description");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "websiteUrl", "error.websiteurl");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "termsAndConditionsUrl", "error.termsandconditionsurl");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "imageUrl", "error.imageurl");
        if (!StringUtils.startsWith(ev.getImageUrl(),"https://")) {
            errors.reject("imageUrl", "error.imageUrl");
        }
        return evaluateValidationResult(errors);
    }

    public static ValidationResult validateEventPrices(EventModification ev, Errors errors) {
        if(!ev.isFreeOfCharge()) {
            if(isCollectionEmpty(ev.getAllowedPaymentProxies())) {
                errors.reject("allowedPaymentProxies", "error.allowedpaymentproxies");
            }
            if(ev.getRegularPrice() == null || BigDecimal.ZERO.compareTo(ev.getRegularPrice()) <= 0) {
                errors.reject("regularPrice", "error.regularprice");
            }
            if(ev.getVat() == null || BigDecimal.ZERO.compareTo(ev.getRegularPrice()) < 0) {
                errors.reject("vat", "error.vat");
            }
            ValidationUtils.rejectIfEmptyOrWhitespace(errors, "currency", "error.currency");
        }
        if(ev.getAvailableSeats() < 1) {
            errors.reject("availableSeats", "error.availableseats");
        }
        return evaluateValidationResult(errors);
    }

    private static boolean isCollectionEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    private static ValidationResult evaluateValidationResult(Errors errors) {
        if (errors.hasFieldErrors()) {
            return ValidationResult.failed(errors.getFieldErrors()
                    .stream().map(ValidationResult.ValidationError::fromFieldError)
                    .collect(Collectors.toList()));
        }
        return ValidationResult.success();
    }
}
