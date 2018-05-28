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
package alfio.controller.form;

import alfio.manager.PaypalManager;
import alfio.model.*;
import alfio.model.result.ValidationResult;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ErrorsCode;
import alfio.util.Validator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.io.Serializable;
import java.util.*;

// step 2 : payment/claim tickets
//
@Data
public class PaymentForm implements Serializable {
    private String stripeToken;
    private String paypalPaymentId;
    private String paypalPayerID;
    private String email;
    private String fullName;
    private String firstName;
    private String lastName;
    private String billingAddress;
    private String hmac;
    private Boolean cancelReservation;
    private Boolean termAndConditionsAccepted;
    private Boolean privacyPolicyAccepted;
    private PaymentProxy paymentMethod;
    private Boolean expressCheckoutRequested;
    private boolean postponeAssignment = false;
    private String vatCountryCode;
    private String vatNr;
    private boolean invoiceRequested = false;
    private Map<String, UpdateTicketOwnerForm> tickets = new HashMap<>();

    private static void rejectIfOverLength(BindingResult bindingResult, String field, String errorCode,
            String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            bindingResult.rejectValue(field, errorCode);
        }
    }

    public String getToken() {
        if(paymentMethod == PaymentProxy.STRIPE) {
            return stripeToken;
        } else if(paymentMethod == PaymentProxy.PAYPAL) {
            return paypalPaymentId;
        } else {
            return null;
        }
    }

    public boolean hasPaypalTokens() {
        return StringUtils.isNotBlank(paypalPayerID) && StringUtils.isNotBlank(paypalPaymentId);
    }

    public void validate(BindingResult bindingResult, TotalPrice reservationCost, Event event,
                         List<TicketFieldConfiguration> fieldConf) {

        List<PaymentProxy> allowedPaymentMethods = event.getAllowedPaymentProxies();

        Optional<PaymentProxy> paymentProxyOptional = Optional.ofNullable(paymentMethod);
        PaymentProxy paymentProxy = paymentProxyOptional.filter(allowedPaymentMethods::contains).orElse(PaymentProxy.STRIPE);
        boolean priceGreaterThanZero = reservationCost.getPriceWithVAT() > 0;
        boolean multiplePaymentMethods = allowedPaymentMethods.size() > 1;
        if (multiplePaymentMethods && priceGreaterThanZero && !paymentProxyOptional.isPresent()) {
            bindingResult.reject(ErrorsCode.STEP_2_MISSING_PAYMENT_METHOD);
        } else if (priceGreaterThanZero && (paymentProxy == PaymentProxy.STRIPE && StringUtils.isBlank(stripeToken))) {
            bindingResult.reject(ErrorsCode.STEP_2_MISSING_STRIPE_TOKEN);
        }

        if(Objects.isNull(termAndConditionsAccepted) || !termAndConditionsAccepted
            || (StringUtils.isNotEmpty(event.getPrivacyPolicyUrl()) && (Objects.isNull(privacyPolicyAccepted) || !privacyPolicyAccepted)) ) {
            bindingResult.reject(ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }
        
        email = StringUtils.trim(email);

        fullName = StringUtils.trim(fullName);
        firstName = StringUtils.trim(firstName);
        lastName = StringUtils.trim(lastName);

        billingAddress = StringUtils.trim(billingAddress);

        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "email", ErrorsCode.STEP_2_EMPTY_EMAIL);
        rejectIfOverLength(bindingResult, "email", ErrorsCode.STEP_2_MAX_LENGTH_EMAIL, email, 255);

        if(event.mustUseFirstAndLastName()) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "firstName", ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
            rejectIfOverLength(bindingResult, "firstName", ErrorsCode.STEP_2_MAX_LENGTH_FIRSTNAME, fullName, 255);
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "lastName", ErrorsCode.STEP_2_EMPTY_LASTNAME);
            rejectIfOverLength(bindingResult, "lastName", ErrorsCode.STEP_2_MAX_LENGTH_LASTNAME, fullName, 255);
        } else {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "fullName", ErrorsCode.STEP_2_EMPTY_FULLNAME);
            rejectIfOverLength(bindingResult, "fullName", ErrorsCode.STEP_2_MAX_LENGTH_FULLNAME, fullName, 255);
        }


        rejectIfOverLength(bindingResult, "billingAddress", ErrorsCode.STEP_2_MAX_LENGTH_BILLING_ADDRESS,
                billingAddress, 450);
        if(invoiceRequested) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddress", ErrorsCode.STEP_2_EMPTY_BILLING_ADDRESS);
        }

        if (email != null && !email.contains("@") && !bindingResult.hasFieldErrors("email")) {
            bindingResult.rejectValue("email", ErrorsCode.STEP_2_INVALID_EMAIL);
        }

        if (hasPaypalTokens() && !PaypalManager.isValidHMAC(new CustomerName(fullName, firstName, lastName, event), email, billingAddress, hmac, event)) {
            bindingResult.reject(ErrorsCode.STEP_2_INVALID_HMAC);
        }

        if(!postponeAssignment) {
            boolean success = Optional.ofNullable(tickets)
                .filter(m -> !m.isEmpty())
                .map(m -> m.entrySet().stream().map(e -> Validator.validateTicketAssignment(e.getValue(), fieldConf, Optional.empty(), event)))
                .filter(s -> s.allMatch(ValidationResult::isSuccess))
                .isPresent();
            if(!success) {
                bindingResult.reject(ErrorsCode.STEP_2_MISSING_ATTENDEE_DATA);
            }
        }
    }

    public Boolean shouldCancelReservation() {
        return Optional.ofNullable(cancelReservation).orElse(false);
    }

    public static PaymentForm fromExistingReservation(TicketReservation reservation) {
        PaymentForm form = new PaymentForm();
        form.setFirstName(reservation.getFirstName());
        form.setLastName(reservation.getLastName());
        form.setBillingAddress(reservation.getBillingAddress());
        form.setEmail(reservation.getEmail());
        form.setFullName(reservation.getFullName());
        form.setVatCountryCode(reservation.getVatCountryCode());
        form.setVatNr(reservation.getVatNr());
        form.setInvoiceRequested(reservation.isInvoiceRequested());
        return form;
    }

    public boolean getHasVatCountryCode() {
        return !StringUtils.isEmpty(vatCountryCode);
    }
}
