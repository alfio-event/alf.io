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
package alfio.controller;

import alfio.controller.api.support.TicketHelper;
import alfio.controller.support.TemplateProcessor;
import alfio.controller.support.TicketDecorator;
import alfio.manager.EventManager;
import alfio.manager.FileUploadManager;
import alfio.manager.NotificationManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.system.Configuration;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ImageUtil;
import alfio.util.LocaleUtil;
import alfio.util.TemplateManager;
import com.google.zxing.WriterException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Optional;

import static alfio.model.system.ConfigurationKeys.ALLOW_FREE_TICKETS_CANCELLATION;

@Controller
public class TicketController {

    private final OrganizationRepository organizationRepository;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final TemplateManager templateManager;
    private final NotificationManager notificationManager;
    private final EventManager eventManager;
    private final ConfigurationManager configurationManager;
    private final FileUploadManager fileUploadManager;
    private final TicketHelper ticketHelper;

    @Autowired
    public TicketController(OrganizationRepository organizationRepository,
                            TicketReservationManager ticketReservationManager,
                            TicketCategoryRepository ticketCategoryRepository,
                            TemplateManager templateManager,
                            NotificationManager notificationManager,
                            EventManager eventManager,
                            ConfigurationManager configurationManager,
                            FileUploadManager fileUploadManager,
                            TicketHelper ticketHelper) {
        this.organizationRepository = organizationRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.templateManager = templateManager;
        this.notificationManager = notificationManager;
        this.eventManager = eventManager;
        this.configurationManager = configurationManager;
        this.fileUploadManager = fileUploadManager;
        this.ticketHelper = ticketHelper;
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}", method = RequestMethod.GET)
    public String showTicket(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, 
            @PathVariable("ticketIdentifier") String ticketIdentifier,
            @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
            Locale locale,
            Model model) {
        return internalShowTicket(eventName, reservationId, ticketIdentifier, ticketEmailSent, model, "success", locale);

    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/view", method = RequestMethod.GET)
    public String showTicketFromTicketDetail(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
                                             @PathVariable("ticketIdentifier") String ticketIdentifier,
                                             @RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
                                             Model model, Locale locale) {
        return internalShowTicket(eventName, reservationId, ticketIdentifier, ticketEmailSent, model, "ticket/"+ticketIdentifier+"/update", locale);
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/ticket/{ticketIdentifier}/update", method = RequestMethod.GET)
    public String showTicketForUpdate(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId,
            @PathVariable("ticketIdentifier") String ticketIdentifier, Model model, Locale locale) {

        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        if(!oData.isPresent()) {
            return "redirect:/event/" + eventName + "/reservation/" + reservationId;
        }
        Triple<Event, TicketReservation, Ticket> data = oData.get();
        Event event = data.getLeft();
        TicketCategory ticketCategory = ticketCategoryRepository.getById(data.getRight().getCategoryId(), event.getId());
        Organization organization = organizationRepository.getById(event.getOrganizationId());

        boolean enableFreeCancellation = configurationManager.getBooleanConfigValue(Configuration.from(event.getOrganizationId(), event.getId(), ticketCategory.getId(), ALLOW_FREE_TICKETS_CANCELLATION), false);
        Ticket ticket = data.getRight();
        model.addAttribute("ticketAndCategory", Pair.of(eventManager.getTicketCategoryById(ticket.getCategoryId(), event.getId()), new TicketDecorator(ticket, enableFreeCancellation, eventManager.checkTicketCancellationPrerequisites().apply(ticket), "ticket/"+ticket.getUuid()+"/view", ticketHelper.findTicketFieldConfigurationAndValue(event.getId(), ticket.getId(), locale))))//
                .addAttribute("reservation", data.getMiddle())//
                .addAttribute("event", event)//
                .addAttribute("ticketCategory", ticketCategory)//
                .addAttribute("countries", ticketHelper.getLocalizedCountries(locale))
                .addAttribute("organization", organization)//
                .addAttribute("pageTitle", "show-ticket.header.title");

        return "/event/update-ticket";
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/send-ticket-by-email", method = RequestMethod.POST)
    public String sendTicketByEmail(@PathVariable("eventName") String eventName,
                                    @PathVariable("reservationId") String reservationId,
                                    @PathVariable("ticketIdentifier") String ticketIdentifier,
                                    HttpServletRequest request) throws Exception {
        
        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        if(!oData.isPresent()) {
            return "redirect:/event/" + eventName + "/reservation/" + reservationId;
        }
        Triple<Event, TicketReservation, Ticket> data = oData.get();
        
        Ticket ticket = data.getRight();
        Event event = data.getLeft();
        Locale locale = LocaleUtil.getTicketLanguage(ticket, request);

        TicketReservation reservation = data.getMiddle();
        notificationManager.sendTicketByEmail(ticket,
            event, locale, TemplateProcessor.buildPartialEmail(event, organizationRepository, reservation, templateManager, ticketReservationManager.ticketUpdateUrl(reservation.getId(), event, ticket.getUuid()), request),
            preparePdfTicket(request, event, reservation, ticket));
        return "redirect:/event/" + eventName + "/reservation/" + reservationId
                + ("ticket".equals(request.getParameter("from")) ? ("/" + ticket.getUuid()) : "/success") + "?ticket-email-sent=true";
    }
    

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/download-ticket", method = RequestMethod.GET)
    public void generateTicketPdf(@PathVariable("eventName") String eventName,
            @PathVariable("reservationId") String reservationId,
            @PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletRequest request, HttpServletResponse response) throws IOException, WriterException {

        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        if(!oData.isPresent()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        Triple<Event, TicketReservation, Ticket> data = oData.get();
        
        Ticket ticket = data.getRight();
        
        response.setContentType("application/pdf");
        response.addHeader("Content-Disposition", "attachment; filename=ticket-" + ticketIdentifier + ".pdf");
        try (OutputStream os = response.getOutputStream()) {
            preparePdfTicket(request, data.getLeft(), data.getMiddle(), ticket).generate(ticket).createPDF(os);
        }
    }
    
    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/code.png", method = RequestMethod.GET)
    public void generateTicketCode(@PathVariable("eventName") String eventName,
            @PathVariable("reservationId") String reservationId,
            @PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletResponse response) throws IOException, WriterException {
        
        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        if(!oData.isPresent()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        
        Triple<Event, TicketReservation, Ticket> data = oData.get();
        
        Event event = data.getLeft();
        Ticket ticket = data.getRight();
        
        String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
        
        response.setContentType("image/png");
        response.getOutputStream().write(ImageUtil.createQRCode(qrCodeText));
        
    }

    @RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/cancel-ticket", method = RequestMethod.POST)
    public String cancelTicket(@PathVariable("eventName") String eventName,
                             @PathVariable("reservationId") String reservationId,
                             @RequestParam("ticketId") String ticketIdentifier) {
        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        oData.ifPresent(triple -> ticketReservationManager.releaseTicket(triple.getLeft(), triple.getMiddle(), triple.getRight()));
        return "redirect:/event/" + eventName + "/reservation/" + reservationId;
    }

    private PartialTicketPDFGenerator preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) throws WriterException, IOException {
        TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
        Organization organization = organizationRepository.getById(event.getOrganizationId());
        return TemplateProcessor.buildPartialPDFTicket(LocaleUtil.getTicketLanguage(ticket, request), event, ticketReservation, ticketCategory, organization, templateManager, fileUploadManager);
    }

    private String internalShowTicket(String eventName, String reservationId, String ticketIdentifier, boolean ticketEmailSent, Model model, String backSuffix, Locale locale) {
        Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
        if(!oData.isPresent()) {
            return "redirect:/event/" + eventName + "/reservation/" + reservationId;
        }
        Triple<Event, TicketReservation, Ticket> data = oData.get();


        TicketCategory ticketCategory = ticketCategoryRepository.getById(data.getRight().getCategoryId(), data.getLeft().getId());
        Organization organization = organizationRepository.getById(data.getLeft().getOrganizationId());

        TicketReservation reservation = data.getMiddle();
        model.addAttribute("ticket", data.getRight())//
            .addAttribute("reservation", reservation)//
            .addAttribute("event", data.getLeft())//
            .addAttribute("ticketCategory", ticketCategory)//
            .addAttribute("organization", organization)//
            .addAttribute("ticketEmailSent", ticketEmailSent)
            .addAttribute("deskPaymentRequired", Optional.ofNullable(reservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired())
            .addAttribute("backSuffix", backSuffix)
            .addAttribute("userLanguage", locale.getLanguage())
            .addAttribute("pageTitle", "show-ticket.header.title");

        return "/event/show-ticket";
    }
}
