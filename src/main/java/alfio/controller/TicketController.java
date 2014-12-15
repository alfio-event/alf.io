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

import alfio.controller.support.TemplateManager;
import alfio.controller.support.TemplateProcessor;
import alfio.manager.NotificationManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.PDFTemplateBuilder;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.user.Organization;
import alfio.repository.TicketCategoryRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.util.TicketUtil;
import com.google.zxing.WriterException;
import com.lowagie.text.DocumentException;
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

@Controller
public class TicketController {

	private final OrganizationRepository organizationRepository;
	private final TicketReservationManager ticketReservationManager;
	private final TicketCategoryRepository ticketCategoryRepository;
	private final TemplateManager templateManager;
	private final NotificationManager notificationManager;

	@Autowired
	public TicketController(OrganizationRepository organizationRepository,
							TicketReservationManager ticketReservationManager,
							TicketCategoryRepository ticketCategoryRepository,
							TemplateManager templateManager,
							NotificationManager notificationManager) {
		this.organizationRepository = organizationRepository;
		this.ticketReservationManager = ticketReservationManager;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.templateManager = templateManager;
		this.notificationManager = notificationManager;
	}
	
	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}", method = RequestMethod.GET)
	public String showTicket(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, 
			@PathVariable("ticketIdentifier") String ticketIdentifier,
			@RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
			Model model) {
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		}
		Triple<Event, TicketReservation, Ticket> data = oData.get();
		
		
		TicketCategory ticketCategory = ticketCategoryRepository.getById(data.getRight().getCategoryId(), data.getLeft().getId());
		Organization organization = organizationRepository.getById(data.getLeft().getOrganizationId());
		
		model.addAttribute("ticket", data.getRight())//
				.addAttribute("reservation", data.getMiddle())//
				.addAttribute("event", data.getLeft())//
				.addAttribute("ticketCategory", ticketCategory)//
				.addAttribute("organization", organization)//
				.addAttribute("ticketEmailSent", ticketEmailSent)
				.addAttribute("pageTitle", "show-ticket.header.title");
		
		return "/event/show-ticket";
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/send-ticket-by-email", method = RequestMethod.POST)
	public String sendTicketByEmail(@PathVariable("eventName") String eventName,
									@PathVariable("reservationId") String reservationId,
									@PathVariable("ticketIdentifier") String ticketIdentifier,
									HttpServletRequest request,
									Locale locale) throws Exception {
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		}
		Triple<Event, TicketReservation, Ticket> data = oData.get();
		
		Ticket ticket = data.getRight();
		Event event = data.getLeft();

		notificationManager.sendTicketByEmail(ticket,
				event, locale, TemplateProcessor.buildEmail(event, organizationRepository, data.getMiddle(), ticket, templateManager, request),
				preparePdfTicket(request, event, data.getMiddle(), ticket));
		return "redirect:/event/" + eventName + "/reservation/" + reservationId
				+ ("ticket".equals(request.getParameter("from")) ? ("/" + ticket.getUuid()) : "/success") + "?ticket-email-sent=true";
	}
	

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/download-ticket", method = RequestMethod.GET)
	public void generateTicketPdf(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId,
			@PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletRequest request, HttpServletResponse response) throws IOException, DocumentException, WriterException {

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
			preparePdfTicket(request, data.getLeft(), data.getMiddle(), ticket).build().createPDF(os);
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
		response.getOutputStream().write(TicketUtil.createQRCode(qrCodeText));
		
	}

	private PDFTemplateBuilder preparePdfTicket(HttpServletRequest request, Event event, TicketReservation ticketReservation, Ticket ticket) throws WriterException, IOException {
		TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		return TemplateProcessor.buildPDFTicket(request, event, ticketReservation, ticket, ticketCategory, organization, templateManager);
	}
}
