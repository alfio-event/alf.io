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

import alfio.model.Event;
import alfio.model.TotalPrice;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ErrorsCode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class PaymentForm {

    private String stripeToken;
    private String paypalPaymentId;
    private String paypalPayerID;
    private PaymentProxy paymentMethod;
    private Boolean termAndConditionsAccepted;
    private Boolean privacyPolicyAccepted;
    private String hmac;
    private Boolean backFromOverview;
    private Boolean cancelReservation;


    public String getToken() {
        if (paymentMethod == PaymentProxy.STRIPE) {
            return stripeToken;
        } else if (paymentMethod == PaymentProxy.PAYPAL) {
            return paypalPaymentId;
        } else {
            return null;
        }
    }

    public boolean hasPaypalTokens() {
        return StringUtils.isNotBlank(paypalPayerID) && StringUtils.isNotBlank(paypalPaymentId);
    }

    public void validate(BindingResult bindingResult, Event event, TotalPrice reservationCost) {
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

        if (Objects.isNull(termAndConditionsAccepted) || !termAndConditionsAccepted
            || (StringUtils.isNotEmpty(event.getPrivacyPolicyUrl()) && (Objects.isNull(privacyPolicyAccepted) || !privacyPolicyAccepted))) {
            bindingResult.reject(ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }

        if (hasPaypalTokens() /*&& !PaypalManager.isValidHMAC(new CustomerName(fullName, firstName, lastName, event), email, billingAddress, hmac, event)*/) {
            bindingResult.reject(ErrorsCode.STEP_2_INVALID_HMAC);
        }
    }

    public Boolean shouldCancelReservation() {
        return Optional.ofNullable(cancelReservation).orElse(false);
    }

    public boolean isBackFromOverview() {
        return Optional.ofNullable(backFromOverview).orElse(false);
    }
}
