package alfio.controller.form;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import alfio.manager.PaypalManager;
import alfio.model.CustomerName;
import alfio.model.Event;
import alfio.model.TicketFieldConfiguration;
import alfio.model.TotalPrice;
import alfio.model.result.ValidationResult;
import alfio.model.transaction.PaymentProxy;
import alfio.util.ErrorsCode;
import alfio.util.Validator;
import lombok.Data;

@Data
public class ValidatePaymentForm {
	private PaymentForm form;

	// Constructor
	public ValidatePaymentForm(PaymentForm form) {
		this.form = form;
	}

	private static void rejectIfOverLength(BindingResult bindingResult, String field, String errorCode,
			String value, int maxLength) {
		if (value != null && value.length() > maxLength) {
			bindingResult.rejectValue(field, errorCode);
		}
	}

	private boolean isMissingPaymentMethod(TotalPrice reservationCost, List<PaymentProxy> allowedPaymentMethods,
			Optional<PaymentProxy> paymentProxyOptional) {
		boolean priceGreaterThanZero = reservationCost.getPriceWithVAT() > 0;
		boolean multiplePaymentMethods = allowedPaymentMethods.size() > 1;

		return multiplePaymentMethods && priceGreaterThanZero && !paymentProxyOptional.isPresent();
	}

	private boolean isMissingStripeToken(TotalPrice reservationCost, PaymentProxy paymentProxy) {
		boolean priceGreaterThanZero = reservationCost.getPriceWithVAT() > 0;
		return priceGreaterThanZero && 
				(paymentProxy == PaymentProxy.STRIPE && StringUtils.isBlank(form.getPaymentMethodContent().getStripeToken()));
	}

	private boolean isTermAndConditionAcceptedMissing() {
		return(Objects.isNull(form.getTermAndConditionsAccepted()) ||
				!form.getTermAndConditionsAccepted());
	}

	private boolean isInvaildEmail(BindingResult bindingResult) {
		return form.getEmail() != null && !form.getEmail().contains("@") && !bindingResult.hasFieldErrors("email");
	}

	public void validate(BindingResult bindingResult, TotalPrice reservationCost, Event event,
			List<TicketFieldConfiguration> fieldConf) {

		List<PaymentProxy> allowedPaymentMethods = event.getAllowedPaymentProxies();
		Optional<PaymentProxy> paymentProxyOptional = Optional.ofNullable(form.getPaymentMethod());
		PaymentProxy paymentProxy = paymentProxyOptional.filter(allowedPaymentMethods::contains).orElse(PaymentProxy.STRIPE);

		if (isMissingPaymentMethod(reservationCost, allowedPaymentMethods, paymentProxyOptional)) {
			bindingResult.reject(ErrorsCode.STEP_2_MISSING_PAYMENT_METHOD);
		} else if (isMissingStripeToken(reservationCost, paymentProxy)) {
			bindingResult.reject(ErrorsCode.STEP_2_MISSING_STRIPE_TOKEN);
		}

		if(isTermAndConditionAcceptedMissing()) 
			bindingResult.reject(ErrorsCode.STEP_2_TERMS_NOT_ACCEPTED);

		//trim payment information
		form.getCustomerInformation().trimInformation();;

		ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "email", ErrorsCode.STEP_2_EMPTY_EMAIL);
		rejectIfOverLength(bindingResult, "email", ErrorsCode.STEP_2_MAX_LENGTH_EMAIL, form.getEmail(), 255);

		if(event.mustUseFirstAndLastName()) {
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "firstName", ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
			rejectIfOverLength(bindingResult, "firstName", ErrorsCode.STEP_2_MAX_LENGTH_FIRSTNAME,
					form.getCustomerInformation().getFullName(), 255);
			
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "lastName", ErrorsCode.STEP_2_EMPTY_LASTNAME);
			rejectIfOverLength(bindingResult, "lastName", ErrorsCode.STEP_2_MAX_LENGTH_LASTNAME, 
					form.getCustomerInformation().getFullName(), 255);
		} else {
			ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "fullName", ErrorsCode.STEP_2_EMPTY_FULLNAME);
			rejectIfOverLength(bindingResult, "fullName", ErrorsCode.STEP_2_MAX_LENGTH_FULLNAME,
					form.getCustomerInformation().getFullName(), 255);
		}

		rejectIfOverLength(bindingResult, "billingAddress", ErrorsCode.STEP_2_MAX_LENGTH_BILLING_ADDRESS,
				form.getCustomerInformation().getBillingAddress(), 450);

		if (isInvaildEmail(bindingResult)) {
			bindingResult.rejectValue("email", ErrorsCode.STEP_2_INVALID_EMAIL);
		}

		if (form.hasPaypalTokens() && !PaypalManager.isValidHMAC(form.getCustomerInformation(),
				form.getPaymentMethodContent().getHmac(), event)) {
			bindingResult.reject(ErrorsCode.STEP_2_INVALID_HMAC);
		}

		if(!form.isPostponeAssignment()) {
			boolean success = Optional.ofNullable(form.getTickets())
					.filter(m -> !m.isEmpty())
					.map(m -> m.entrySet().stream().map(e -> Validator.validateTicketAssignment(e.getValue(), fieldConf, Optional.empty(), event)))
					.filter(s -> s.allMatch(ValidationResult::isSuccess))
					.isPresent();
			if(!success) {
				bindingResult.reject(ErrorsCode.STEP_2_MISSING_ATTENDEE_DATA);
			}
		}
	}

}
