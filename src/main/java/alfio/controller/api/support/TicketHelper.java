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
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TicketHelper {

    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TicketRepository ticketRepository;
    private final TemplateManager templateManager;
    private final FileUploadManager fileUploadManager;

    @Autowired
    public TicketHelper(TicketReservationManager ticketReservationManager,
                        OrganizationRepository organizationRepository,
                        TicketCategoryRepository ticketCategoryRepository,
                        TicketRepository ticketRepository,
                        TemplateManager templateManager,
                        FileUploadManager fileUploadManager) {
        this.ticketReservationManager = ticketReservationManager;
        this.organizationRepository = organizationRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.ticketRepository = ticketRepository;
        this.templateManager = templateManager;
        this.fileUploadManager = fileUploadManager;
    }

    public Optional<Triple<ValidationResult, Event, Ticket>> assignTicket(String eventName,
                                                                           String reservationId,
                                                                           String ticketIdentifier,
                                                                           UpdateTicketOwnerForm updateTicketOwner,
                                                                           BindingResult bindingResult,
                                                                           HttpServletRequest request,
                                                                           Consumer<Triple<ValidationResult, Event, Ticket>> reservationConsumer,
                                                                           Optional<UserDetails> userDetails) {
        Optional<Triple<ValidationResult, Event, Ticket>> triple = ticketReservationManager.fetchComplete(eventName, reservationId, ticketIdentifier)
                .map(result -> {
                    Ticket t = result.getRight();
                    if(t.getLockedAssignment()) {
                        //in case of locked assignment, fullName and Email will be overwritten
                        updateTicketOwner.setFullName(t.getFullName());
                        updateTicketOwner.setEmail(t.getEmail());
                    }
                    final Event event = result.getLeft();
                    final TicketReservation ticketReservation = result.getMiddle();
                    ValidationResult validationResult = Validator.validateTicketAssignment(updateTicketOwner, bindingResult)
                            .ifSuccess(() -> updateTicketOwner(updateTicketOwner, request, t, event, ticketReservation, userDetails));
                    return Triple.of(validationResult, event, ticketRepository.findByUUID(t.getUuid()));
                });
        triple.ifPresent(reservationConsumer);
        return triple;
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
                preparePdfTicket(request, event, ticketReservation, t),
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
