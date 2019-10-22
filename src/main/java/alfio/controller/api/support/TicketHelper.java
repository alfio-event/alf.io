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

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.*;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.*;
import alfio.model.result.ValidationResult;
import alfio.model.user.Organization;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.EventUtil;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.Validator;
import alfio.util.Validator.AdvancedTicketAssignmentValidator;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@AllArgsConstructor
public class TicketHelper {

    private static Set<TicketReservation.TicketReservationStatus> PENDING_RESERVATION_STATUSES = EnumSet.of(TicketReservation.TicketReservationStatus.PENDING, TicketReservation.TicketReservationStatus.OFFLINE_PAYMENT);

    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TemplateManager templateManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;
    private final EuVatChecker vatChecker;
    private final GroupManager groupManager;
    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;


    public List<TicketFieldConfigurationDescriptionAndValue> findTicketFieldConfigurationAndValue(Ticket ticket) {
        return buildRetrieveFieldValuesFunction().apply(ticket);
    }

    public Function<Ticket, List<TicketFieldConfigurationDescriptionAndValue>> buildRetrieveFieldValuesFunction() {
        return EventUtil.retrieveFieldValues(ticketRepository, ticketFieldRepository, additionalServiceItemRepository);
    }

    public Function<String, Integer> getTicketUUIDToCategoryId() {
        return (uuid) -> ticketRepository.getTicketCategoryByUIID(uuid);
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                           String ticketIdentifier,
                                                                           UpdateTicketOwnerForm updateTicketOwner,
                                                                           Optional<Errors> bindingResult,
                                                                           HttpServletRequest request,
                                                                           Consumer<Triple<ValidationResult, Event, Ticket>> reservationConsumer,
                                                                           Optional<UserDetails> userDetails,
                                                                           boolean addPrefix) {

        Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.fetchComplete(eventName, ticketIdentifier)
                .map(result -> assignTicket(updateTicketOwner, bindingResult, request, userDetails, result, addPrefix ? "tickets["+ticketIdentifier+"]" : ""));
        triple.ifPresent(reservationConsumer);
        return triple;
    }

    private Triple<ValidationResult, Event, Ticket> assignTicket(UpdateTicketOwnerForm updateTicketOwner,
                                                                 Optional<Errors> bindingResult,
                                                                 HttpServletRequest request,
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
        List<TicketFieldConfiguration> fieldConf = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId());
        var sameCountryValidator = new SameCountryValidator(configurationManager, extensionManager, event.getOrganizationId(), event.getId(), ticketReservation.getId(), vatChecker);
        AdvancedTicketAssignmentValidator advancedValidator = new AdvancedTicketAssignmentValidator(sameCountryValidator,
            new GroupManager.WhitelistValidator(event.getId(), groupManager));

        Validator.AdvancedValidationContext context = new Validator.AdvancedValidationContext(updateTicketOwner, fieldConf, t.getCategoryId(), t.getUuid(), formPrefix);
        ValidationResult validationResult = Validator.validateTicketAssignment(updateTicketOwner, fieldConf, bindingResult, event, formPrefix, sameCountryValidator, t.getCategoryId())
                .or(Validator.performAdvancedValidation(advancedValidator, context, bindingResult.orElse(null)))
                .ifSuccess(() -> updateTicketOwner(updateTicketOwner, request, t, event, ticketReservation, userDetails));
        return Triple.of(validationResult, event, ticketRepository.findByUUID(t.getUuid()));
    }

    /**
     * This method has been implemented explicitly for PayPal, since we need to pre-assign tickets before payment, in order to keep the data inserted by the customer
     */
    public Optional<Triple<ValidationResult, Event, Ticket>> preAssignTicket(String eventName,
                                                                          String reservationId,
                                                                          String ticketIdentifier,
                                                                          UpdateTicketOwnerForm updateTicketOwner,
                                                                          Optional<Errors> bindingResult,
                                                                          HttpServletRequest request,
                                                                          Consumer<Triple<ValidationResult, Event, Ticket>> reservationConsumer,
                                                                          Optional<UserDetails> userDetails) {

        Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.from(eventName, reservationId, ticketIdentifier)
            .filter(temp -> PENDING_RESERVATION_STATUSES.contains(temp.getMiddle().getStatus()) && temp.getRight().getStatus() == Ticket.TicketStatus.PENDING)
            .map(result -> assignTicket(updateTicketOwner, bindingResult, request, userDetails, result, "tickets["+ticketIdentifier+"]"));
        triple.ifPresent(reservationConsumer);
        return triple;
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                          String ticketIdentifier,
                                                                          UpdateTicketOwnerForm updateTicketOwner,
                                                                          Optional<Errors> bindingResult,
                                                                          HttpServletRequest request,
                                                                          Model model) {
        return assignTicket(eventName, ticketIdentifier, updateTicketOwner, bindingResult, request, t -> {
            model.addAttribute("value", t.getRight());
            model.addAttribute("validationResult", t.getLeft());
            model.addAttribute("countries", getLocalizedCountries(RequestContextUtils.getLocale(request)));
            model.addAttribute("event", t.getMiddle());
        }, Optional.empty(), false);
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> directTicketAssignment(String eventName,
                                                                                    String reservationId,
                                                                                    String email,
                                                                                    String fullName,
                                                                                    String firstName,
                                                                                    String lastName,
                                                                                    String userLanguage,
                                                                                    Optional<Errors> bindingResult,
                                                                                    HttpServletRequest request,
                                                                                    Model model) {
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
        return assignTicket(eventName, ticketUuid, form, bindingResult, request, model);
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

    private void updateTicketOwner(UpdateTicketOwnerForm updateTicketOwner, HttpServletRequest request, Ticket t, Event event, TicketReservation ticketReservation, Optional<UserDetails> userDetails) {
        Locale language = Optional.ofNullable(updateTicketOwner.getUserLanguage())
                .filter(StringUtils::isNotBlank)
                .map(Locale::forLanguageTag)
                .orElseGet(() -> RequestContextUtils.getLocale(request));
        TicketCategory category = ticketCategoryRepository.getById(t.getCategoryId());
        ticketReservationManager.updateTicketOwner(t, language, event, updateTicketOwner,
                getConfirmationTextBuilder(request, event, ticketReservation, t, category),
                getOwnerChangeTextBuilder(request, t, event),
                userDetails);
        if(t.hasBeenSold() && !groupManager.findLinks(event.getId(), t.getCategoryId()).isEmpty()) {
            ticketRepository.forbidReassignment(Collections.singletonList(t.getId()));
        }
    }

    private PartialTicketTextGenerator getOwnerChangeTextBuilder(HttpServletRequest request, Ticket t, Event event) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        String ticketUrl = ticketReservationManager.ticketUpdateUrl(event, t.getUuid());
        return TemplateProcessor.buildEmailForOwnerChange(event, t, organization, ticketUrl, templateManager, LocaleUtil.getTicketLanguage(t, request));
    }

    private PartialTicketTextGenerator getConfirmationTextBuilder(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket, TicketCategory ticketCategory) {
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        String ticketUrl = ticketReservationManager.ticketUpdateUrl(event, ticket.getUuid());
        return TemplateProcessor.buildPartialEmail(event, organization, ticketReservation, ticketCategory, templateManager, ticketUrl, request);
    }

}
