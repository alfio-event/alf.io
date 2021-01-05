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

import alfio.model.PurchaseContext;
import alfio.model.TotalPrice;
import alfio.model.transaction.PaymentMethod;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ErrorsCode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Data
public class PaymentForm implements Serializable {

    private String gatewayToken;
    private PaymentProxy paymentProxy;
    private PaymentMethod selectedPaymentMethod;
    private Boolean termAndConditionsAccepted;
    private Boolean privacyPolicyAccepted;
    private String hmac;
    private String captcha;


    public void validate(BindingResult bindingResult, PurchaseContext purchaseContext, TotalPrice reservationCost) {
        List<PaymentProxy> allowedProxies = purchaseContext.getAllowedPaymentProxies();

        Optional<PaymentProxy> paymentProxyOptional = Optional.ofNullable(paymentProxy);
        boolean priceGreaterThanZero = reservationCost.getPriceWithVAT() > 0;
        boolean multipleProxies = allowedProxies.size() > 1;
        if (multipleProxies && priceGreaterThanZero && paymentProxyOptional.isEmpty()) {
            bindingResult.reject(ErrorsCode.STEP_2_MISSING_PAYMENT_METHOD);
        }

        if (Objects.isNull(termAndConditionsAccepted) || !termAndConditionsAccepted
            || (StringUtils.isNotEmpty(purchaseContext.getPrivacyPolicyUrl()) && (Objects.isNull(privacyPolicyAccepted) || !privacyPolicyAccepted))) {
            bindingResult.reject(ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);
        }
    }
}
