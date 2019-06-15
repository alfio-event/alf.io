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

import alfio.manager.SameCountryValidator;
import alfio.model.*;
import alfio.model.TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.util.ErrorsCode;
import alfio.util.Validator;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static alfio.util.ErrorsCode.STEP_2_INVALID_VAT;
import static alfio.util.ErrorsCode.STEP_2_MISSING_ATTENDEE_DATA;

// step 2 : contact/claim tickets
//
@Data
public class ContactAndTicketsForm implements Serializable {

    private String email;
    private String fullName;
    private String firstName;
    private String lastName;
    private String billingAddress;
    private String customerReference;
    private Boolean cancelReservation;

    private Boolean expressCheckoutRequested;
    private boolean postponeAssignment = false;
    private String vatCountryCode;
    private String vatNr;
    private boolean invoiceRequested = false;
    private Map<String, UpdateTicketOwnerForm> tickets = new HashMap<>();
    //
    private String billingAddressCompany;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingAddressZip;
    private String billingAddressCity;

    private Boolean addCompanyBillingDetails;
    private Boolean skipVatNr;
    private Boolean backFromOverview;
    //

    // https://github.com/alfio-event/alf.io/issues/573
    private String italyEInvoicingFiscalCode;
    private ItalianEInvoicing.ReferenceType italyEInvoicingReferenceType;
    private String italyEInvoicingReferenceAddresseeCode;
    private String italyEInvoicingReferencePEC;
    //

    private static void rejectIfOverLength(BindingResult bindingResult, String field, String errorCode,
            String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            bindingResult.rejectValue(field, errorCode);
        }
    }



    public void validate(BindingResult bindingResult, Event event, List<TicketFieldConfiguration> fieldConf, SameCountryValidator vatValidator, Map<ConfigurationKeys, Boolean> formValidationParameters, Function<String, Integer> fromTicketUUIDToTicketCategoryId) {


        
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



        if(invoiceRequested) {
            /*if(companyVatChecked) {
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressCompany", "error.emptyField");
                rejectIfOverLength(bindingResult, "billingAddressCompany", "error.tooLong", billingAddressCompany, 256);
            }*/

            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressLine1", "error.emptyField");
            rejectIfOverLength(bindingResult, "billingAddressLine1", "error.tooLong", billingAddressLine1, 256);

            rejectIfOverLength(bindingResult, "billingAddressLine2", "error.tooLong", billingAddressLine2, 256);

            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressZip", "error.emptyField");
            rejectIfOverLength(bindingResult, "billingAddressZip", "error.tooLong", billingAddressZip, 51);

            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressCity", "error.emptyField");
            rejectIfOverLength(bindingResult, "billingAddressCity", "error.tooLong", billingAddressCity, 256);

            ValidationUtils.rejectIfEmpty(bindingResult, "vatCountryCode", "error.emptyField");

            if(StringUtils.trimToNull(billingAddressCompany) != null && !canSkipVatNrCheck()) {
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "vatNr", "error.emptyField");
            }

        }

        // https://github.com/alfio-event/alf.io/issues/573
        // only for IT and only if enabled!
        if (formValidationParameters.getOrDefault(ConfigurationKeys.ENABLE_ITALY_E_INVOICING, false) && "IT".equals(vatCountryCode)) {

            // mandatory
            ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingFiscalCode", "error.emptyField");
            rejectIfOverLength(bindingResult, "italyEInvoicingFiscalCode", "error.tooLong", italyEInvoicingFiscalCode, 256);
            //

            //
            ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferenceType", "error.italyEInvoicingReferenceTypeSelectValue");
            //
            if (ItalianEInvoicing.ReferenceType.ADDRESSEE_CODE == italyEInvoicingReferenceType) {
                ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferenceAddresseeCode", "error.emptyField");
                italyEInvoicingReferenceAddresseeCode = StringUtils.trim(italyEInvoicingReferenceAddresseeCode);
                if (italyEInvoicingReferenceAddresseeCode != null) {
                    if (italyEInvoicingReferenceAddresseeCode.length() != 7) {
                        bindingResult.rejectValue("italyEInvoicingReferenceAddresseeCode", "error.lengthMustBe7");
                    }

                    if (!StringUtils.isAlphanumeric(italyEInvoicingReferenceAddresseeCode)) {
                        bindingResult.rejectValue("italyEInvoicingReferenceAddresseeCode", "error.alphanumeric");
                    }
                }
            }
            if (ItalianEInvoicing.ReferenceType.PEC == italyEInvoicingReferenceType) {
                ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferencePEC", "error.emptyField");
            }

        }

        if (email != null && !email.contains("@") && !bindingResult.hasFieldErrors("email")) {
            bindingResult.rejectValue("email", ErrorsCode.STEP_2_INVALID_EMAIL);
        }

        if(!postponeAssignment) {
            Optional<List<ValidationResult>> validationResults = Optional.ofNullable(tickets)
                .filter(m -> !m.isEmpty())
                .map(m -> m.entrySet().stream().map(e -> Validator.validateTicketAssignment(e.getValue(),
                    fieldConf, Optional.of(bindingResult), event, "tickets[" + e.getKey() + "]", vatValidator, fromTicketUUIDToTicketCategoryId.apply(e.getKey()))))
                .map(s -> s.collect(Collectors.toList()));

            boolean success = validationResults
                .filter(l -> l.stream().allMatch(ValidationResult::isSuccess))
                .isPresent();
            if(!success) {
                String errorCode = validationResults.filter(this::containsVatValidationError).isPresent() ? STEP_2_INVALID_VAT : STEP_2_MISSING_ATTENDEE_DATA;
                bindingResult.reject(errorCode);
            }
        }
    }

    private boolean containsVatValidationError(List<ValidationResult> l) {
        return l.stream().anyMatch(v -> !v.isSuccess() && v.getErrorDescriptors().stream().anyMatch(ed -> ed.getCode().equals(STEP_2_INVALID_VAT)));
    }

    public Boolean shouldCancelReservation() {
        return Optional.ofNullable(cancelReservation).orElse(false);
    }

    public static ContactAndTicketsForm fromExistingReservation(TicketReservation reservation, TicketReservationAdditionalInfo additionalInfo) {
        ContactAndTicketsForm form = new ContactAndTicketsForm();
        form.setFirstName(reservation.getFirstName());
        form.setLastName(reservation.getLastName());
        form.setBillingAddress(reservation.getBillingAddress());
        form.setEmail(reservation.getEmail());
        form.setFullName(reservation.getFullName());
        form.setVatCountryCode(reservation.getVatCountryCode());
        form.setVatNr(reservation.getVatNr());
        form.setInvoiceRequested(reservation.isInvoiceRequested());
        form.setCustomerReference(reservation.getCustomerReference());


        form.setBillingAddressCompany(additionalInfo.getBillingAddressCompany());
        form.setBillingAddressLine1(additionalInfo.getBillingAddressLine1());
        form.setBillingAddressLine2(additionalInfo.getBillingAddressLine2());
        form.setBillingAddressZip(additionalInfo.getBillingAddressZip());
        form.setBillingAddressCity(additionalInfo.getBillingAddressCity());
        form.setAddCompanyBillingDetails(additionalInfo.getAddCompanyBillingDetails());
        form.setSkipVatNr(additionalInfo.getSkipVatNr());

        //https://github.com/alfio-event/alf.io/issues/573
        Optional.ofNullable(additionalInfo.getInvoicingAdditionalInfo())
            .map(TicketReservationInvoicingAdditionalInfo::getItalianEInvoicing)
            .ifPresent(iei -> {
                form.setItalyEInvoicingFiscalCode(iei.getFiscalCode());
                form.setItalyEInvoicingReferenceType(iei.getReferenceType());
                form.setItalyEInvoicingReferenceAddresseeCode(iei.getAddresseeCode());
                form.setItalyEInvoicingReferencePEC(iei.getPec());
            });

        return form;
    }

    public boolean getHasVatCountryCode() {
        return !StringUtils.isEmpty(vatCountryCode);
    }

    public boolean isBackFromOverview() {
        return Boolean.TRUE.equals(backFromOverview);
    }

    public boolean canSkipVatNrCheck() {
        return Boolean.TRUE.equals(skipVatNr);
    }

    public boolean isBusiness() {
        return getAddCompanyBillingDetails() && !canSkipVatNrCheck() && invoiceRequested;
    }

    // https://github.com/alfio-event/alf.io/issues/573
    public boolean getItalyEInvoicingTypeAddresseeCode() {
        return italyEInvoicingReferenceType == ItalianEInvoicing.ReferenceType.ADDRESSEE_CODE;
    }

    public boolean getItalyEInvoicingTypePEC() {
        return italyEInvoicingReferenceType == ItalianEInvoicing.ReferenceType.PEC;
    }

    public boolean getItalyEInvoicingTypeNone() {
        return italyEInvoicingReferenceType == ItalianEInvoicing.ReferenceType.NONE;
    }
    //

    public boolean getAddCompanyBillingDetails() {
        return Boolean.TRUE.equals(addCompanyBillingDetails);
    }
}
