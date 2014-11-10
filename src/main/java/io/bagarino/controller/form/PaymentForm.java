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
package io.bagarino.controller.form;

import io.bagarino.controller.ErrorsCode;
import io.bagarino.manager.TicketReservationManager.TotalPrice;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.util.Objects;
import java.util.Optional;

// step 2 : payment/claim tickets
//
@Data
public class PaymentForm {
	private String stripeToken;
	private String email;
	private String fullName;
	private String billingAddress;
	private Boolean cancelReservation;
	private Boolean termAndConditionsAccepted;

	private static void rejectIfOverLength(BindingResult bindingResult, String field, String errorCode,
			String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			bindingResult.rejectValue(field, errorCode);
		}
	}

	public void validate(BindingResult bindingResult, TotalPrice reservationCost) {

		if (reservationCost.getPriceWithVAT() > 0 && StringUtils.isBlank(stripeToken)) {
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
	}

	public Boolean shouldCancelReservation() {
		return Optional.ofNullable(cancelReservation).orElse(false);
	}
}
