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
package alfio.controller.api.support;

import alfio.controller.form.AdditionalServiceLinkForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.result.ValidationResult;
import alfio.model.user.Organization;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.PurchaseContextFieldRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.*;
import alfio.util.Validator.AdvancedTicketAssignmentValidator;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.PurchaseContextFieldConfiguration.Context.ADDITIONAL_SERVICE;
import static alfio.model.PurchaseContextFieldConfiguration.Context.ATTENDEE;
import static java.util.Objects.requireNonNullElse;

@Component
@AllArgsConstructor
public class TicketHelper {

    private static final Set<TicketReservation.TicketReservationStatus> PENDING_RESERVATION_STATUSES = EnumSet.of(TicketReservation.TicketReservationStatus.PENDING, TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);

    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;
    private final TicketRepository ticketRepository;
    private final TemplateManager templateManager;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final PurchaseContextFieldManager purchaseContextFieldManager;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final EuVatChecker vatChecker;
    private final GroupManager groupManager;
    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;


    public BiFunction<Ticket, Event, List<FieldConfigurationDescriptionAndValue>> buildRetrieveFieldValuesFunction(boolean formatValues) {
        return EventUtil.retrieveFieldValues(ticketRepository, purchaseContextFieldManager, additionalServiceItemRepository, formatValues);
    }

    public Function<String, Integer> getTicketUUIDToCategoryId() {
        return ticketRepository::getTicketCategoryByUIID;
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                           String ticketIdentifier,
                                                                           UpdateTicketOwnerForm updateTicketOwner,
                                                                           Optional<BindingResult> bindingResult,
                                                                           Locale fallbackLocale,
                                                                           Optional<UserDetails> userDetails,
                                                                           boolean addPrefix) {

        return ticketReservationManager.fetchComplete(eventName, ticketIdentifier)
                .map(result -> assignTicket(updateTicketOwner, bindingResult, fallbackLocale, userDetails, result, addPrefix ? "tickets["+ticketIdentifier+"]" : ""));
    }

    private Triple<ValidationResult, Event, Ticket> assignTicket(UpdateTicketOwnerForm updateTicketOwner,
                                                                 Optional<BindingResult> bindingResult,
                                                                 Locale fallbackLocale,
                                                                 Optional<UserDetails> userDetails,
                                                                 Triple<Event, TicketReservation, Ticket> result,
                                                                 String formPrefix) {
        Ticket t = result.getRight();
        final Event event = result.getLeft();
        if(t.getLockedAssignment()) {
            //in case of locked assignment, fullName and Email will be overwritten
            updateTicketOwner.setFirstName(t.getFirstName());
            updateTicketOwner.setLastName(t.getLastName());
            updateTicketOwner.setFullName(t.getFullName());
            updateTicketOwner.setEmail(t.getEmail());
        }

        final TicketReservation ticketReservation = result.getMiddle();
        List<PurchaseContextFieldConfiguration> fieldConf = purchaseContextFieldRepository.findAdditionalFieldsForEvent(event.getId());
        var sameCountryValidator = new SameCountryValidator(configurationManager, extensionManager, event, ticketReservation.getId(), vatChecker);
        AdvancedTicketAssignmentValidator advancedValidator = new AdvancedTicketAssignmentValidator(sameCountryValidator,
            new GroupManager.WhitelistValidator(event.getId(), groupManager));


        var additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(event.getId(), ticketReservation.getId());

        List<Ticket> ticketsInReservation = ticketRepository.findTicketsInReservation(ticketReservation.getId());
        var ticketFieldFilterer = new Validator.AdditionalFieldsFilterer(fieldConf,
            ticketsInReservation,
            event.supportsLinkedAdditionalServices(),
            additionalServiceItems);

        Validator.AdvancedValidationContext context = new Validator.AdvancedValidationContext(updateTicketOwner, fieldConf, t.getCategoryId(), t.getUuid(), formPrefix);

        ValidationResult validationResult = Validator.validateTicketAssignment(updateTicketOwner, ticketFieldFilterer.getFieldsForTicket(t.getUuid(), EnumSet.of(ATTENDEE)), bindingResult, event, formPrefix, sameCountryValidator, extensionManager)
                .or(validateAdditionalItemsFields(event, updateTicketOwner, t.getUuid(), ticketFieldFilterer.getFieldsForTicket(t.getUuid(), EnumSet.of(ADDITIONAL_SERVICE)), additionalServiceItems, bindingResult.orElse(null), sameCountryValidator))
                .or(Validator.performAdvancedValidation(advancedValidator, context, bindingResult.orElse(null)))
                .ifSuccess(() -> updateTicketOwner(updateTicketOwner, fallbackLocale, t, event, ticketReservation, userDetails));
        return Triple.of(validationResult, event, ticketsInReservation.stream().filter(t2 -> t2.getUuid().equals(t.getUuid())).findFirst().orElseThrow());
    }

    private ValidationResult validateAdditionalItemsFields(Event event,
                                                           UpdateTicketOwnerForm updateTicketOwner,
                                                           String ticketUuid,
                                                           List<PurchaseContextFieldConfiguration> fieldsForTicket,
                                                           List<AdditionalServiceItem> additionalServiceItems,
                                                           BindingResult bindingResult,
                                                           SameCountryValidator vatValidator) {

        if (!event.supportsLinkedAdditionalServices() || fieldsForTicket.isEmpty() || bindingResult == null) {
            return ValidationResult.success();
        }
        Map<String, List<AdditionalServiceLinkForm>> map = requireNonNullElse(updateTicketOwner.getAdditionalServices(), Map.of());
        var fieldForms = Objects.requireNonNullElse(map.get(ticketUuid), List.<AdditionalServiceLinkForm>of());
        int formFieldsSize = (int) fieldForms.stream().flatMap(f -> f.getAdditional().keySet().stream()).distinct().count();
        if (formFieldsSize != fieldsForTicket.size()) {
            // form contains wrong fields. Reject all values
            bindingResult.reject(ErrorsCode.EMPTY_FIELD);
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("", ErrorsCode.EMPTY_FIELD));
        } else if (formFieldsSize == 0) {
            return ValidationResult.success();
        }

        var bookedItems = fieldForms.stream().map(AdditionalServiceLinkForm::getAdditionalServiceItemId).collect(Collectors.toSet());

        // validate that the input form only contains items that are actually linked to the current ticket
        int count = additionalServiceItemRepository.countMatchingItemsForTicket(ticketUuid, bookedItems);
        ValidationResult result;

        if (count != bookedItems.size()) {
            result = ValidationResult.failed(new ValidationResult.ErrorDescriptor("", ErrorsCode.EMPTY_FIELD));
        } else {
            result = ValidationResult.success();
        }

        for (int i = 0; i < formFieldsSize; i++) {
            var form = fieldForms.get(i);
            result = result.or(Validator.validateAdditionalItemFieldsForTicket(form, fieldsForTicket, bindingResult, "additionalServices["+ticketUuid+"]["+i+"]", vatValidator, fieldForms, additionalServiceItems));
        }

        return result;
    }

    /**
     * This method has been implemented explicitly for PayPal, since we need to pre-assign tickets before payment, in order to keep the data inserted by the customer
     */
    public Optional<Triple<ValidationResult, Event, Ticket>> preAssignTicket(String eventName,
                                                                             String reservationId,
                                                                             String ticketIdentifier,
                                                                             UpdateTicketOwnerForm updateTicketOwner,
                                                                             Optional<BindingResult> bindingResult,
                                                                             Locale fallbackLocale) {

        return ticketReservationManager.from(eventName, reservationId, ticketIdentifier)
            .filter(temp -> PENDING_RESERVATION_STATUSES.contains(temp.getMiddle().getStatus()) && temp.getRight().getStatus() == Ticket.TicketStatus.PENDING)
            .map(result -> assignTicket(updateTicketOwner, bindingResult, fallbackLocale,Optional.empty(), result,"tickets["+ticketIdentifier+"]"));
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                          String ticketIdentifier,
                                                                          UpdateTicketOwnerForm updateTicketOwner,
                                                                          Optional<BindingResult> bindingResult,
                                                                          Locale locale) {
        return assignTicket(eventName, ticketIdentifier, updateTicketOwner, bindingResult, locale, Optional.empty(), false);
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> directTicketAssignment(String eventName,
                                                                                    String reservationId,
                                                                                    String email,
                                                                                    String fullName,
                                                                                    String firstName,
                                                                                    String lastName,
                                                                                    String userLanguage,
                                                                                    Optional<BindingResult> bindingResult,
                                                                                    Locale locale) {
        List<Ticket> tickets = ticketReservationManager.findTicketsInReservation(reservationId);
        if(tickets.size() > 1) {
            return Optional.empty();
        }
        String ticketUuid = tickets.get(0).getUuid();
        UpdateTicketOwnerForm form = new UpdateTicketOwnerForm();
        form.setAdditional(Collections.emptyMap());
        form.setEmail(email);
        form.setFullName(fullName);
        form.setFirstName(firstName);
        form.setLastName(lastName);
        form.setUserLanguage(userLanguage);
        return assignTicket(eventName, ticketUuid, form, bindingResult, locale);
    }

    public static List<Pair<String, String>> getLocalizedCountries(Locale locale) {
        return mapISOCountries(Stream.of(Locale.getISOCountries()), locale);
    }

    public static List<Pair<String, String>> getLocalizedEUCountriesForVat(Locale locale, String euCountries) {
        return fixVAT(mapISOCountries(Stream.of(Locale.getISOCountries())
            .filter(isoCode -> StringUtils.contains(euCountries, isoCode) || "GR".equals(isoCode)), locale));
    }

    public static List<Pair<String, String>> getLocalizedCountriesForVat(Locale locale) {
        return fixVAT(mapISOCountries(Stream.of(Locale.getISOCountries()), locale));
    }

    private static List<Pair<String, String>> fixVAT(List<Pair<String, String>> countries) {
        return countries.stream()
            .map(kv -> Pair.of(FIX_ISO_CODE_FOR_VAT.getOrDefault(kv.getKey(), kv.getKey()), kv.getValue()))
            .collect(Collectors.toList());
    }


    //
    public static final Map<String, String> FIX_ISO_CODE_FOR_VAT = Collections.singletonMap("GR", "EL");

    private static List<Pair<String, String>> mapISOCountries(Stream<String> isoCountries, Locale locale) {
        return isoCountries
            .map(isoCode -> Pair.of(isoCode, new Locale("", isoCode).getDisplayCountry(locale)))
            .sorted(Comparator.comparing(Pair::getRight))
            .collect(Collectors.toList());
    }

    private void updateTicketOwner(UpdateTicketOwnerForm updateTicketOwner, Locale fallBackLocale, Ticket t, Event event, TicketReservation ticketReservation, Optional<UserDetails> userDetails) {
        Locale language = Optional.ofNullable(updateTicketOwner.getUserLanguage())
                .filter(StringUtils::isNotBlank)
                .map(LocaleUtil::forLanguageTag)
                .orElse(fallBackLocale);
        var ticketLanguage = LocaleUtil.getTicketLanguage(t, fallBackLocale);
        ticketReservationManager.updateTicketOwner(t, language, event, updateTicketOwner,
                getConfirmationTextBuilder(ticketLanguage, event, ticketReservation, t),
                getOwnerChangeTextBuilder(ticketLanguage, t, event),
                userDetails);
        if(t.hasBeenSold() && !groupManager.findLinks(event.getId(), t.getCategoryId()).isEmpty()) {
            ticketRepository.forbidReassignment(Collections.singletonList(t.getId()));
        }
    }

    private PartialTicketTextGenerator getOwnerChangeTextBuilder(Locale ticketLanguage, Ticket t, Event event) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        String ticketUrl = ReservationUtil.ticketUpdateUrl(event, t, configurationManager);
        return TemplateProcessor.buildEmailForOwnerChange(event, t, organization, ticketUrl, templateManager, ticketLanguage);
    }

    public PartialTicketTextGenerator getConfirmationTextBuilder(Locale ticketLanguage,
                                                                 Event event,
                                                                 TicketReservation ticketReservation,
                                                                 Ticket ticket) {
        return ticketReservationManager.getTicketEmailGenerator(event,
            ticketReservation,
            ticketLanguage,
            ticketReservationManager.retrieveAttendeeAdditionalInfoForTicket(ticket));
    }
}
