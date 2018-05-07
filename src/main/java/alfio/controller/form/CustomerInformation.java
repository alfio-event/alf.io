package alfio.controller.form;

import org.apache.commons.lang3.StringUtils;

import alfio.model.TicketReservation;
import lombok.Data;

@Data
public class CustomerInformation {
	private String email;
	private String fullName;
	private String firstName;
	private String lastName;
	private String billingAddress;
	
	public void setInformationByTicketReservation(TicketReservation reservation) {
		setFirstName(reservation.getFirstName());
        setLastName(reservation.getLastName());
        setBillingAddress(reservation.getBillingAddress());
        setEmail(reservation.getEmail());
        setFullName(reservation.getFullName());
	}
	
	public void trimInformation() {
		email = StringUtils.trim(email);
		fullName = StringUtils.trim(fullName);
		firstName = StringUtils.trim(firstName);
		lastName = StringUtils.trim(lastName);
		billingAddress = StringUtils.trim(billingAddress);
	}
}
