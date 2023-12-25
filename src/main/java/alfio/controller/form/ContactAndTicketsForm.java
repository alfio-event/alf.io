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

import alfio.controller.support.CustomBindingResult;
import alfio.manager.ExtensionManager;
import alfio.manager.SameCountryValidator;
import alfio.model.AdditionalServiceItem;
import alfio.model.Event;
import alfio.model.PurchaseContext;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.PurchaseContextFieldConfiguration;
import alfio.model.TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing;
import alfio.model.result.ValidationResult;
import alfio.model.result.WarningMessage;
import alfio.model.system.ConfigurationKeys;
import alfio.util.ErrorsCode;
import alfio.util.ItalianTaxIdValidator;
import alfio.util.Validator;
import lombok.Data;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ValidationUtils;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static alfio.model.PurchaseContextFieldConfiguration.Context.ADDITIONAL_SERVICE;
import static alfio.model.PurchaseContextFieldConfiguration.Context.ATTENDEE;
import static alfio.util.ErrorsCode.*;
import static alfio.util.Validator.validateFieldConfiguration;

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
    private String billingAddressState;

    private Boolean addCompanyBillingDetails;
    private Boolean skipVatNr;
    //

    // https://github.com/alfio-event/alf.io/issues/573
    private String italyEInvoicingFiscalCode;
    private ItalianEInvoicing.ReferenceType italyEInvoicingReferenceType;
    private String italyEInvoicingReferenceAddresseeCode;
    private String italyEInvoicingReferencePEC;
    private boolean italyEInvoicingSplitPayment;
    private boolean differentSubscriptionOwner;
    private UpdateSubscriptionOwnerForm subscriptionOwner;

    private Map<String, List<AdditionalServiceLinkForm>> additionalServices = new HashMap<>();

    //

    private static void rejectIfOverLength(BindingResult bindingResult, String field, String errorCode,
            String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            bindingResult.rejectValue(field, errorCode, new Object[] {maxLength}, null);
        }
    }



    public void validate(CustomBindingResult bindingResult,
                         PurchaseContext purchaseContext,
                         SameCountryValidator vatValidator,
                         Map<ConfigurationKeys, Boolean> formValidationParameters,
                         Optional<Validator.AdditionalFieldsFilterer> additionalFieldsFilterer,
                         boolean reservationRequiresPayment,
                         ExtensionManager extensionManager,
                         Supplier<List<AdditionalServiceItem>> additionalServiceItemsSupplier) {


        formalValidation(bindingResult, formValidationParameters.getOrDefault(ConfigurationKeys.ENABLE_ITALY_E_INVOICING, false), reservationRequiresPayment);

        var fieldsFilterer = additionalFieldsFilterer.orElseThrow();
        purchaseContext.event().ifPresent(event -> {
            checkAdditionalServiceItemsLink(event, bindingResult, additionalServiceItemsSupplier, vatValidator, fieldsFilterer);
            if(!postponeAssignment) {
                Optional<List<ValidationResult>> validationResults = Optional.ofNullable(tickets)
                    .filter(m -> !m.isEmpty())
                    .map(m -> m.entrySet().stream().map(e -> {
                        var filteredForTicket = fieldsFilterer.getFieldsForTicket(e.getKey(), EnumSet.of(ATTENDEE));
                        return Validator.validateTicketAssignment(e.getValue(), filteredForTicket, Optional.of(bindingResult), event, "tickets[" + e.getKey() + "]", vatValidator, extensionManager);
                    }))
                    .map(s -> s.collect(Collectors.toList()));

                boolean success = validationResults
                    .filter(l -> l.stream().allMatch(ValidationResult::isSuccess))
                    .isPresent();
                if(!success) {
                    String errorCode = validationResults.filter(this::containsVatValidationError).isPresent() ? STEP_2_INVALID_VAT : STEP_2_MISSING_ATTENDEE_DATA;
                    bindingResult.reject(errorCode);
                }
            }
        });

        if (purchaseContext.ofType(PurchaseContextType.subscription)) {
            if (differentSubscriptionOwner) {
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "subscriptionOwner.firstName", ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "subscriptionOwner.lastName", ErrorsCode.STEP_2_EMPTY_LASTNAME);
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "subscriptionOwner.email", ErrorsCode.STEP_2_EMPTY_EMAIL);
            }

            for(PurchaseContextFieldConfiguration fieldConf : fieldsFilterer.getFieldsForSubscription()) {
                validateFieldConfiguration(subscriptionOwner, vatValidator, bindingResult, "subscriptionOwner.", fieldConf);
            }
        }

    }

    private void checkAdditionalServiceItemsLink(Event event,
                                                 BindingResult bindingResult,
                                                 Supplier<List<AdditionalServiceItem>> additionalServiceItemsCount,
                                                 SameCountryValidator vatValidator,
                                                 Validator.AdditionalFieldsFilterer additionalFieldsFilterer) {
        if (!event.supportsLinkedAdditionalServices()) {
            return;
        }
        Map<String, List<AdditionalServiceLinkForm>> form = Objects.requireNonNullElseGet(additionalServices, Map::of);
        var additionalServiceItems = additionalServiceItemsCount.get();
        if (additionalServiceItems.size() != form.values().stream().mapToInt(List::size).sum()
            || form.values().stream().anyMatch(v -> v.stream().anyMatch(Predicate.not(AdditionalServiceLinkForm::isValid)))) {
            bindingResult.reject(STEP_2_ADDITIONAL_ITEMS_NOT_ASSIGNED);
        }
        var result = ValidationResult.success();
        for (var ticketAndFields : form.entrySet()) {
            var filteredForTicket = additionalFieldsFilterer.getFieldsForTicket(ticketAndFields.getKey(), EnumSet.of(ADDITIONAL_SERVICE));
            var fieldForms = ticketAndFields.getValue();
            for (int i = 0; i < fieldForms.size(); i++) {
                result = result.or(Validator.validateAdditionalItemFieldsForTicket(fieldForms.get(i), filteredForTicket, bindingResult, "additionalServices["+ticketAndFields.getKey()+"]["+i+"]", vatValidator, fieldForms, additionalServiceItems));
            }
        }

        boolean success = result.isSuccess();
        if(!success) {
            String errorCode = containsVatValidationError(List.of(result)) ? STEP_2_INVALID_VAT : STEP_2_MISSING_ATTENDEE_DATA;
            bindingResult.reject(errorCode);
        }
    }

    public void formalValidation(CustomBindingResult bindingResult,
                                 boolean italianEInvoicingEnabled,
                                 boolean reservationRequiresPayment) {
        email = StringUtils.trim(email);

        fullName = StringUtils.trim(fullName);
        firstName = StringUtils.trim(firstName);
        lastName = StringUtils.trim(lastName);

        billingAddress = StringUtils.trim(billingAddress);

        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "email", ErrorsCode.STEP_2_EMPTY_EMAIL);
        rejectIfOverLength(bindingResult, "email", ErrorsCode.STEP_2_MAX_LENGTH_EMAIL, email, 255);


        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "firstName", ErrorsCode.STEP_2_EMPTY_FIRSTNAME);
        rejectIfOverLength(bindingResult, "firstName", ErrorsCode.STEP_2_MAX_LENGTH_FIRSTNAME, fullName, 255);
        ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "lastName", ErrorsCode.STEP_2_EMPTY_LASTNAME);
        rejectIfOverLength(bindingResult, "lastName", ErrorsCode.STEP_2_MAX_LENGTH_LASTNAME, fullName, 255);


        if(invoiceRequested) {
            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressLine1", ErrorsCode.EMPTY_FIELD);
            rejectIfOverLength(bindingResult, "billingAddressLine1", "error.tooLong", billingAddressLine1, 256);

            rejectIfOverLength(bindingResult, "billingAddressLine2", "error.tooLong", billingAddressLine2, 256);

            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressZip", ErrorsCode.EMPTY_FIELD);
            rejectIfOverLength(bindingResult, "billingAddressZip", "error.tooLong", billingAddressZip, 51);

            ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "billingAddressCity", ErrorsCode.EMPTY_FIELD);
            rejectIfOverLength(bindingResult, "billingAddressCity", "error.tooLong", billingAddressCity, 256);

            ValidationUtils.rejectIfEmpty(bindingResult, "vatCountryCode", ErrorsCode.EMPTY_FIELD);

            if(StringUtils.trimToNull(billingAddressCompany) != null && !canSkipVatNrCheck()) {
                ValidationUtils.rejectIfEmptyOrWhitespace(bindingResult, "vatNr", ErrorsCode.EMPTY_FIELD);
            }

        }

        // https://github.com/alfio-event/alf.io/issues/573
        // only for IT and only if enabled!
        if (italianEInvoicingEnabled && StringUtils.isNotEmpty(vatCountryCode) && reservationRequiresPayment) {
            // mandatory
            ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingFiscalCode", ErrorsCode.EMPTY_FIELD);
            rejectIfOverLength(bindingResult, "italyEInvoicingFiscalCode", "error.tooLong", italyEInvoicingFiscalCode, 28);
        }

        if (italianEInvoicingEnabled && "IT".equals(vatCountryCode) && reservationRequiresPayment) {
            //
            ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferenceType", "error.italyEInvoicingReferenceTypeSelectValue");
            //
            if (ItalianEInvoicing.ReferenceType.ADDRESSEE_CODE == italyEInvoicingReferenceType) {
                ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferenceAddresseeCode", ErrorsCode.EMPTY_FIELD);
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
                ValidationUtils.rejectIfEmpty(bindingResult, "italyEInvoicingReferencePEC", ErrorsCode.EMPTY_FIELD);
            }

            if(billingAddressState == null || billingAddressState.strip().length() != 2) {
                bindingResult.rejectValue("billingAddressState", "error.length", new Object[] { 2 }, null);
            } else if(!StringUtils.isAlpha(billingAddressState)) {
                bindingResult.rejectValue("billingAddressState", "error.restrictedValue");
            }

            if(StringUtils.isNotEmpty(vatNr) && !ItalianTaxIdValidator.validateVatId(vatNr)) {
                bindingResult.rejectValue("vatNr", "error.STEP_2_INVALID_VAT");
            }

            boolean fiscalCodeValid = ItalianTaxIdValidator.validateFiscalCode(italyEInvoicingFiscalCode);
            if(!fiscalCodeValid) {
                bindingResult.rejectValue("italyEInvoicingFiscalCode", "error.restrictedValue");
            } else if(!ItalianTaxIdValidator.fiscalCodeMatchesWithName(firstName, lastName, italyEInvoicingFiscalCode)) {
                bindingResult.addWarning("warning.fiscal-code-name-mismatch");
            }

            if(StringUtils.length(StringUtils.trimToNull(billingAddressZip)) != 5) {
                bindingResult.rejectValue("billingAddressZip", "error.length", new Object[] { 5 }, null);
            } else if(!StringUtils.isNumeric(billingAddressZip)) {
                bindingResult.rejectValue("billingAddressZip", "error.restrictedValue");
            }

        }

        if (email != null && !bindingResult.hasFieldErrors("email")) {
            if (!Validator.isEmailValid(email)) {
                bindingResult.rejectValue("email", ErrorsCode.STEP_2_INVALID_EMAIL, new Object[] {}, null);
            } else if (!Validator.isCanonicalMailAddress(email)) {
                bindingResult.addWarning(new WarningMessage(ErrorsCode.STEP_2_EMAIL_TYPO, List.of(email)));
            }
        }
    }

    private boolean containsVatValidationError(List<ValidationResult> l) {
        return l.stream().anyMatch(v -> !v.isSuccess() && v.getErrorDescriptors().stream().anyMatch(ed -> ed.getCode().equals(STEP_2_INVALID_VAT)));
    }

    public boolean canSkipVatNrCheck() {
        return Boolean.TRUE.equals(skipVatNr);
    }

    public boolean isBusiness() {
        return getAddCompanyBillingDetails() && !canSkipVatNrCheck() && invoiceRequested;
    }

    public boolean getAddCompanyBillingDetails() {
        return Boolean.TRUE.equals(addCompanyBillingDetails);
    }

    public boolean hasAdditionalServices() {
        return MapUtils.isNotEmpty(additionalServices);
    }
}
