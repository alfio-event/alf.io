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
package alfio.controller.api;

import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.controller.support.TemplateManager;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.PDFTemplateBuilder;
import alfio.manager.support.TextTemplateBuilder;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import com.google.zxing.WriterException;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/event/{eventName}")
public class EventApiController {

    private final TicketReservationManager ticketReservationManager;
    private final OrganizationRepository organizationRepository;
    private final TemplateManager templateManager;
    private final TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    public EventApiController(TicketReservationManager ticketReservationManager,
                              OrganizationRepository organizationRepository,
                              TemplateManager templateManager,
                              TicketCategoryRepository ticketCategoryRepository) {
        this.ticketReservationManager = ticketReservationManager;
        this.organizationRepository = organizationRepository;
        this.templateManager = templateManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    @RequestMapping(value = "/reservation/{reservationId}/{ticketIdentifier}", method = RequestMethod.POST)
    public ValidationResult assignTicketToPerson(@PathVariable("eventName") String eventName,
                                       @PathVariable("reservationId") String reservationId,
                                       @PathVariable("ticketIdentifier") String ticketIdentifier,
                                       UpdateTicketOwnerForm updateTicketOwner,
                                       BindingResult bindingResult,
                                       HttpServletRequest request,
                                       HttpServletResponse response,
                                       Locale locale) throws Exception {

        return ticketReservationManager.fetchComplete(eventName, reservationId, ticketIdentifier)
                .map(result -> {
                    Ticket t = result.getRight();
                    Validate.isTrue(!t.getLockedAssignment(), "cannot change a locked ticket");
                    final Event event = result.getLeft();
                    final TicketReservation ticketReservation = result.getMiddle();
                    return Validator.validateTicketAssignment(updateTicketOwner, bindingResult)
                            .ifSuccess(() -> updateTicketOwner(updateTicketOwner, request, locale, t, event, ticketReservation));
                }).orElse(ValidationResult.failed());

    }

    private void updateTicketOwner(UpdateTicketOwnerForm updateTicketOwner, HttpServletRequest request, Locale locale, Ticket t, Event event, TicketReservation ticketReservation) {
        ticketReservationManager.updateTicketOwner(t, locale, event, updateTicketOwner,
                getConfirmationTextBuilder(request, t, event, ticketReservation),
                getOwnerChangeTextBuilder(request, t, event),
                preparePdfTicket(request, event, ticketReservation, t));
    }

    private TextTemplateBuilder getOwnerChangeTextBuilder(HttpServletRequest request, Ticket t, Event event) {
        return TemplateProcessor.buildEmailForOwnerChange(t.getEmail(), event, t, organizationRepository, ticketReservationManager, templateManager, request);
    }

    private TextTemplateBuilder getConfirmationTextBuilder(HttpServletRequest request, Ticket t, Event event, TicketReservation ticketReservation) {
        return TemplateProcessor.buildEmail(event, organizationRepository, ticketReservation, t, templateManager, request);
    }

    private PDFTemplateBuilder preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) {
        TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        try {
            return TemplateProcessor.buildPDFTicket(request, event, ticketReservation, ticket, ticketCategory, organization, templateManager);
        } catch (WriterException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
