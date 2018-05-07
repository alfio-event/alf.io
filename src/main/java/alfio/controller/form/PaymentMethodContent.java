package alfio.controller.form;

import org.apache.commons.lang3.StringUtils;

import alfio.model.TicketReservation;
import alfio.model.transaction.PaymentProxy;
import lombok.Data;

@Data
public class PaymentMethodContent {
	private PaymentProxy paymentMethod;
	private String stripeToken;
    private String paypalPaymentId;
    private String paypalPayerID;
    private String hmac;
    private String vatCountryCode;
    private String vatNr;
    
	public void setVatInformationByTicketReservation(TicketReservation reservation) {
		setVatCountryCode(reservation.getVatCountryCode());
        setVatNr(reservation.getVatNr());
	}
	
	public boolean hasPaypalTokens() {
		return StringUtils.isNotBlank(getPaypalPayerID()) && StringUtils.isNotBlank(getPaypalPaymentId());
	}

}
