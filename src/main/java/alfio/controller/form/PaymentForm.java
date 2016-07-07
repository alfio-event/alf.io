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
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ErrorsCode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// step 2 : payment/claim tickets
//
@Data
public class PaymentForm {
    private String stripeToken;
    private String paypalPaymentId;
    private String paypalPayerID;
    private String email;
    private String fullName;
    private String billingAddress;
    private String hmac;
    private Boolean cancelReservation;
    private Boolean termAndConditionsAccepted;
    private PaymentProxy paymentMethod;
    private Boolean expressCheckoutRequested;

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

    public void validate(BindingResult bindingResult, TicketReservationManager.TotalPrice reservationCost, Event event) {

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

        if(Objects.isNull(termAndConditionsAccepted) || !termAndConditionsAccepted) {
            bindingResult.reject(ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }
        
        email = StringUtils.trim(email);
        fullName = StringUtils.trim(fullName);
        billingAddress = StringUtils.trim(billingAddress);

        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "email", ErrorsCode.STEP_2_EMPTY_EMAIL);
        rejectIfOverLength(bindingResult, "email", ErrorsCode.STEP_2_MAX_LENGTH_EMAIL, email, 255);

        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "fullName", ErrorsCode.STEP_2_EMPTY_FULLNAME);
        rejectIfOverLength(bindingResult, "fullName", ErrorsCode.STEP_2_MAX_LENGTH_FULLNAME, fullName, 255);

        rejectIfOverLength(bindingResult, "billingAddress", ErrorsCode.STEP_2_MAX_LENGTH_BILLING_ADDRESS,
                billingAddress, 450);

        if (email != null && !email.contains("@") && !bindingResult.hasFieldErrors("email")) {
            bindingResult.rejectValue("email", ErrorsCode.STEP_2_INVALID_EMAIL);
        }

        if (hasPaypalTokens() && !PaypalManager.isValidHMAC(fullName, email, billingAddress, hmac, event)) {
            bindingResult.reject(ErrorsCode.STEP_2_INVALID_HMAC);
        }
    }

    public Boolean shouldCancelReservation() {
        return Optional.ofNullable(cancelReservation).orElse(false);
    }
}
