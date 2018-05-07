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

import alfio.model.*;
import alfio.model.transaction.PaymentProxy;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.*;

// step 2 : payment/claim tickets
//

@Data
public class PaymentForm implements Serializable {
	// PaymentMethod and payment content(vat information) information
	private PaymentMethodContent paymentMethodContent;
    // Customer information(name, email, address)
    private CustomerInformation customerInformation;
    
    private Boolean cancelReservation;
    private Boolean termAndConditionsAccepted;
    private Boolean expressCheckoutRequested;
    private boolean postponeAssignment = false;
    private boolean invoiceRequested = false;
    
    private Map<String, UpdateTicketOwnerForm> tickets = new HashMap<>();

    public String getToken() {
        if(getPaymentMethod() == PaymentProxy.STRIPE) {
            return this.getPaymentMethodContent().getStripeToken();
        } else if(getPaymentMethod() == PaymentProxy.PAYPAL) {
            return this.getPaymentMethodContent().getPaypalPaymentId();
        } else {
            return null;
        }
    }
    
    // Hide Delegate 
    public String getEmail() {
    	return this.getCustomerInformation().getEmail();
    }
    
    // Hide Delegate
    public PaymentProxy getPaymentMethod() {
    	return this.getPaymentMethodContent().getPaymentMethod();
    }
    
    // Hide Delegate
	public boolean hasPaypalTokens() {
		return this.getPaymentMethodContent().hasPaypalTokens();
	}

    public Boolean shouldCancelReservation() {
        return Optional.ofNullable(getCancelReservation()).orElse(false);
    }

    public static PaymentForm fromExistingReservation(TicketReservation reservation) {
        PaymentForm form = new PaymentForm();
        form.getCustomerInformation().setInformationByTicketReservation(reservation);
        form.getPaymentMethodContent().setVatInformationByTicketReservation(reservation);
        form.setInvoiceRequested(reservation.isInvoiceRequested());
        return form;
    }

    public boolean getHasVatCountryCode() {
        return !StringUtils.isEmpty(this.getPaymentMethodContent().getVatCountryCode());
    }
}
