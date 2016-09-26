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
import alfio.manager.FileUploadManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.*;
import alfio.model.user.Organization;
import alfio.repository.AdditionalServiceItemRepository;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.TicketFieldRepository;
import alfio.repository.TicketRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
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

import static alfio.model.TicketFieldConfiguration.Context.ATTENDEE;

@Component
public class TicketHelper {

    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TemplateManager templateManager;
    private final FileUploadManager fileUploadManager;
    private final TicketFieldRepository ticketFieldRepository;
    private final AdditionalServiceItemRepository additionalServiceItemRepository;

    @Autowired
    public TicketHelper(TicketReservationManager ticketReservationManager,
                        OrganizationRepository organizationRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        TicketRepository ticketRepository,
                        TemplateManager templateManager,
                        FileUploadManager fileUploadManager,
                        TicketFieldRepository ticketFieldRepository,
                        AdditionalServiceItemRepository additionalServiceItemRepository) {
        this.ticketReservationManager = ticketReservationManager;
        this.organizationRepository = organizationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.templateManager = templateManager;
        this.fileUploadManager = fileUploadManager;
        this.ticketFieldRepository = ticketFieldRepository;
        this.additionalServiceItemRepository = additionalServiceItemRepository;
    }

    public List<TicketFieldConfigurationDescriptionAndValue> findTicketFieldConfigurationAndValue(int eventId, Ticket ticket, Locale locale) {
        Map<Integer, TicketFieldDescription> descriptions = ticketFieldRepository.findTranslationsFor(locale, eventId);
        Map<String, TicketFieldValue> values = ticketFieldRepository.findAllByTicketIdGroupedByName(ticket.getId());
        Function<TicketFieldConfiguration, String> extractor = (f) -> Optional.ofNullable(values.get(f.getName())).map(TicketFieldValue::getValue).orElse("");
        List<AdditionalServiceItem> additionalServiceItems = additionalServiceItemRepository.findByReservationUuid(ticket.getTicketsReservationId());
        Set<Integer> additionalServiceIds = additionalServiceItems.stream().map(AdditionalServiceItem::getAdditionalServiceId).collect(Collectors.toSet());
        return ticketFieldRepository.findAdditionalFieldsForEvent(eventId)
            .stream()
            .filter(f -> f.getContext() == ATTENDEE || Optional.ofNullable(f.getAdditionalServiceId()).filter(additionalServiceIds::contains).isPresent())
            .map(f-> {
                int count = Math.max(1, Optional.ofNullable(f.getAdditionalServiceId()).map(id -> (int) additionalServiceItems.stream().filter(i -> i.getAdditionalServiceId() == id).count()).orElse(1));
                return new TicketFieldConfigurationDescriptionAndValue(f, descriptions.getOrDefault(f.getId(), TicketFieldDescription.MISSING_FIELD), count, extractor.apply(f));
            })
            .collect(Collectors.toList());
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                           String reservationId,
                                                                           String ticketIdentifier,
                                                                           UpdateTicketOwnerForm updateTicketOwner,
                                                                           Optional<Errors> bindingResult,
                                                                           HttpServletRequest request,
                                                                           Consumer<Triple<ValidationResult, Event, Ticket>> reservationConsumer,
                                                                           Optional<UserDetails> userDetails) {

        Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.fetchComplete(eventName, reservationId, ticketIdentifier)
                .map(result -> {
                    Ticket t = result.getRight();
                    final Event event = result.getLeft();
                    if(t.getLockedAssignment()) {
                        //in case of locked assignment, fullName and Email will be overwritte
                        updateTicketOwner.setFirstName(t.getFirstName());
                        updateTicketOwner.setLastName(t.getLastName());
                        updateTicketOwner.setFullName(t.getFullName());
                        updateTicketOwner.setEmail(t.getEmail());
                    }

                    final TicketReservation ticketReservation = result.getMiddle();
                    List<TicketFieldConfiguration> fieldConf = ticketFieldRepository.findAdditionalFieldsForEvent(event.getId());
                    ValidationResult validationResult = Validator.validateTicketAssignment(updateTicketOwner, fieldConf, bindingResult, event)
                            .ifSuccess(() -> updateTicketOwner(updateTicketOwner, request, t, event, ticketReservation, userDetails));
                    return Triple.of(validationResult, event, ticketRepository.findByUUID(t.getUuid()));
                });
        triple.ifPresent(reservationConsumer);
        return triple;
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                          String reservationId,
                                                                          String ticketIdentifier,
                                                                          UpdateTicketOwnerForm updateTicketOwner,
                                                                          Optional<Errors> bindingResult,
                                                                          HttpServletRequest request,
                                                                          Model model) {
        return assignTicket(eventName, reservationId, ticketIdentifier, updateTicketOwner, bindingResult, request, t -> {
            model.addAttribute("value", t.getRight());
            model.addAttribute("validationResult", t.getLeft());
            model.addAttribute("countries", getLocalizedCountries(RequestContextUtils.getLocale(request)));
            model.addAttribute("event", t.getMiddle());
        }, Optional.<UserDetails>empty());
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
        return assignTicket(eventName, reservationId, ticketUuid, form, bindingResult, request, model);
    }

    public List<Pair<String, String>> getLocalizedCountries(Locale locale) {
        return Stream.of(Locale.getISOCountries())
                .map(isoCode -> Pair.of(isoCode, new Locale("", isoCode).getDisplayCountry(locale)))
                .sorted(Comparator.comparing(Pair::getRight))
                .collect(Collectors.toList());
    }

    private void updateTicketOwner(UpdateTicketOwnerForm updateTicketOwner, HttpServletRequest request, Ticket t, Event event, TicketReservation ticketReservation, Optional<UserDetails> userDetails) {
        Locale language = Optional.ofNullable(updateTicketOwner.getUserLanguage())
                .filter(StringUtils::isNotBlank)
                .map(Locale::forLanguageTag)
                .orElseGet(() -> RequestContextUtils.getLocale(request));
        ticketReservationManager.updateTicketOwner(t, language, event, updateTicketOwner,
                getConfirmationTextBuilder(request, event, ticketReservation, t),
                getOwnerChangeTextBuilder(request, t, event),
                userDetails);
    }

    private PartialTicketTextGenerator getOwnerChangeTextBuilder(HttpServletRequest request, Ticket t, Event event) {
        return TemplateProcessor.buildEmailForOwnerChange(event, t, organizationRepository, ticketReservationManager, templateManager, LocaleUtil.getTicketLanguage(t, request));
    }

    private PartialTicketTextGenerator getConfirmationTextBuilder(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) {
        return TemplateProcessor.buildPartialEmail(event, organizationRepository, ticketReservation, templateManager, ticketReservationManager.ticketUpdateUrl(ticketReservation.getId(), event, ticket.getUuid()), request);
    }

    private PartialTicketPDFGenerator preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) {
        TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        return TemplateProcessor.buildPartialPDFTicket(LocaleUtil.getTicketLanguage(ticket, request), event, ticketReservation, ticketCategory, organization, templateManager, fileUploadManager);
    }
}
