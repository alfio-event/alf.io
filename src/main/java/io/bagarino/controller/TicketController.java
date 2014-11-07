/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller;

import io.bagarino.controller.form.UpdateTicketOwnerForm;
import io.bagarino.controller.support.TemplateManager;
import io.bagarino.manager.TicketReservationManager;
import io.bagarino.manager.system.Mailer;
import io.bagarino.manager.system.Mailer.Attachment;
import io.bagarino.model.Event;
import io.bagarino.model.Ticket;
import io.bagarino.model.TicketCategory;
import io.bagarino.model.TicketReservation;
import io.bagarino.model.TicketReservation.TicketReservationStatus;
import io.bagarino.model.user.Organization;
import io.bagarino.repository.TicketCategoryRepository;
import io.bagarino.repository.TicketRepository;
import io.bagarino.repository.user.OrganizationRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.xhtmlrenderer.pdf.ITextRenderer;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.DocumentException;

@Controller
public class TicketController {

	private final OrganizationRepository organizationRepository;
	private final TicketRepository ticketRepository;
	private final TicketReservationManager ticketReservationManager;
	private final TicketCategoryRepository ticketCategoryRepository;
	private final Mailer mailer;
	private final MessageSource messageSource;
	private final TemplateManager templateManager;

	@Autowired
	public TicketController(OrganizationRepository organizationRepository, 
			TicketReservationManager ticketReservationManager,
			TicketRepository ticketRepository, 
			TicketCategoryRepository ticketCategoryRepository,
			Mailer mailer,
			MessageSource messageSource,
			TemplateManager templateManager) {
		this.organizationRepository = organizationRepository;
		this.ticketReservationManager = ticketReservationManager;
		this.ticketRepository = ticketRepository;
		this.ticketCategoryRepository = ticketCategoryRepository;
		this.mailer = mailer;
		this.messageSource = messageSource;
		this.templateManager = templateManager;
	}
	
	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}", method = RequestMethod.GET)
	public String showTicket(@PathVariable("eventName") String eventName, @PathVariable("reservationId") String reservationId, 
			@PathVariable("ticketIdentifier") String ticketIdentifier,
			@RequestParam(value = "ticket-email-sent", required = false, defaultValue = "false") boolean ticketEmailSent,
			Model model) {
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
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
				.addAttribute("ticketEmailSent", ticketEmailSent);
		
		return "/event/show-ticket";
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}", method = RequestMethod.POST)
	public String assignTicketToPerson(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId,
			@PathVariable("ticketIdentifier") String ticketIdentifier,
			UpdateTicketOwnerForm updateTicketOwner, BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = fetchComplete(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		}
		
		Ticket t = oData.get().getRight();
		
		Validate.isTrue(!t.getLockedAssignment(), "cannot change a locked ticket");
		
		//TODO: validate email, fullname (not null, maxlength 255)
		//TODO validate the others fields for size too.
		String newEmail = updateTicketOwner.getEmail().trim();
		String newFullName = updateTicketOwner.getFullName().trim();
		ticketRepository.updateTicketOwner(ticketIdentifier, newEmail, newFullName);
		//
		ticketRepository.updateOptionalTicketInfo(ticketIdentifier, updateTicketOwner.getJobTitle(), 
				updateTicketOwner.getCompany(), 
				updateTicketOwner.getPhoneNumber(), 
				updateTicketOwner.getAddress(), 
				updateTicketOwner.getCountry(), 
				updateTicketOwner.getTShirtSize());
		
		if (!newEmail.equals(t.getEmail()) || !newFullName.equals(t.getFullName())) {
			sendTicketByEmail(eventName, reservationId, ticketIdentifier, request, response);
		}
		
		if (!newEmail.equals(t.getEmail())) {
			sendEmailForOwnerChange(updateTicketOwner.getEmail().trim(), oData.get().getLeft(), t, request);
		}
		
		//
		
		return "redirect:/event/" + eventName + "/reservation/" + reservationId;
	}

	private void sendEmailForOwnerChange(String newEmailOwner,
			Event e, Ticket t,
			HttpServletRequest request) {
		
		String eventName = e.getShortName();
		
		Map<String, Object> emailModel = new HashMap<String, Object>();
		emailModel.put("ticket", t);
		emailModel.put("organization", organizationRepository.getById(e.getOrganizationId()));
		emailModel.put("eventName", eventName);
		emailModel.put("previousEmail", t.getEmail());
		emailModel.put("newEmail", newEmailOwner);
		emailModel.put("reservationUrl", ticketReservationManager.reservationUrl(t.getTicketsReservationId()));
		String subject = messageSource.getMessage("ticket-has-changed-owner-subject", new Object[] {eventName}, RequestContextUtils.getLocale(request));
		String emailText = templateManager.render("/io/bagarino/templates/ticket-has-changed-owner-txt.ms", emailModel, request);
		mailer.send(t.getEmail(), subject, emailText, Optional.empty());
	}

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/send-ticket-by-email", method = RequestMethod.POST)
	public String sendTicketByEmail(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId, 
			@PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			return "redirect:/event/" + eventName + "/reservation/" + reservationId;
		}
		Triple<Event, TicketReservation, Ticket> data = oData.get();
		
		Ticket ticket = data.getRight();
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		preparePdfTicket(request, response, data.getLeft(), data.getMiddle(), ticket).createPDF(baos);
		
		Attachment attachment = new Attachment("ticket-" + ticketIdentifier + ".pdf", new ByteArrayResource(baos.toByteArray()), "application/pdf");
		
		Map<String, Object> model = new HashMap<>();
		model.put("organization", organizationRepository.getById(data.getLeft().getOrganizationId()));
		model.put("event", data.getLeft());
		model.put("ticketReservation", data.getMiddle());
		model.put("ticket", ticket);
    	String ticketEmailTxt = templateManager.render("/io/bagarino/templates/ticket-email-txt.ms", model, request);
		
		mailer.send(ticket.getEmail(), messageSource.getMessage("ticket-email-subject", new Object[] {data.getLeft().getShortName()}, RequestContextUtils.getLocale(request)), ticketEmailTxt, Optional.empty(), attachment);
		
		return "redirect:/event/" + eventName + "/reservation/" + reservationId
				+ ("ticket".equals(request.getParameter("from")) ? ("/" + ticket.getUuid()) : "") + "?ticket-email-sent=true";
	}
	

	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/download-ticket", method = RequestMethod.GET)
	public void generateTicketPdf(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId,
			@PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletRequest request, HttpServletResponse response) throws IOException, DocumentException, WriterException {

		Optional<Triple<Event, TicketReservation, Ticket>> oData = fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		Triple<Event, TicketReservation, Ticket> data = oData.get();
		
		Ticket ticket = data.getRight();
		
		response.setContentType("application/pdf");
		response.addHeader("Content-Disposition", "attachment; filename=ticket-" + ticketIdentifier + ".pdf");
		try (OutputStream os = response.getOutputStream()) {
			preparePdfTicket(request, response, data.getLeft(), data.getMiddle(), ticket).createPDF(os);
		}
	}
	
	@RequestMapping(value = "/event/{eventName}/reservation/{reservationId}/{ticketIdentifier}/code.png", method = RequestMethod.GET)
	public void generateTicketCode(@PathVariable("eventName") String eventName,
			@PathVariable("reservationId") String reservationId,
			@PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletResponse response) throws IOException, WriterException {
		
		Optional<Triple<Event, TicketReservation, Ticket>> oData = fetchCompleteAndAssigned(eventName, reservationId, ticketIdentifier);
		if(!oData.isPresent()) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			return;
		}
		
		Triple<Event, TicketReservation, Ticket> data = oData.get();
		
		Event event = data.getLeft();
		Ticket ticket = data.getRight();
		
		String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
		
		response.setContentType("image/png");
		response.getOutputStream().write(createQRCode(qrCodeText));
		
	}
	
	private Optional<Triple<Event, TicketReservation, Ticket>> fetchComplete(String eventName, String reservationId, String ticketIdentifier) {
		return ticketReservationManager.from(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
			if(t.getMiddle().getStatus() == TicketReservationStatus.COMPLETE) {
				return Optional.of(t);
			} else {
				return Optional.empty();
			}
		});
	}
	
	/**
	 * Return a fully present triple only if the values are present (obviously) and the the reservation has a COMPLETE status and the ticket is considered assigned.
	 * 
	 * @param eventName
	 * @param reservationId
	 * @param ticketIdentifier
	 * @return
	 */
	private Optional<Triple<Event, TicketReservation, Ticket>> fetchCompleteAndAssigned(String eventName, String reservationId, String ticketIdentifier) {
		return fetchComplete(eventName, reservationId, ticketIdentifier).flatMap((t) -> {
					if(t.getRight().getAssigned()) {
						return Optional.of(t);
					} else {
						return Optional.empty();
					}
				});
	}

	private ITextRenderer preparePdfTicket(HttpServletRequest request, HttpServletResponse response, Event event, TicketReservation ticketReservation, Ticket ticket) throws WriterException, IOException {
		
		TicketCategory ticketCategory = ticketCategoryRepository.getById(ticket.getCategoryId(), event.getId());
		Organization organization = organizationRepository.getById(event.getOrganizationId());
		
		//
		String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
		//
		
		//
		Map<String, Object> model = new HashMap<String, Object>();
		model.put("ticket", ticket);
		model.put("reservation", ticketReservation);
		model.put("ticketCategory", ticketCategory);
		model.put("event", event);
		model.put("organization", organization);
		model.put("qrCodeDataUri", "data:image/png;base64," + Base64.getEncoder().encodeToString(createQRCode(qrCodeText)));
			
		String page = templateManager.render("/io/bagarino/templates/ticket.ms", model, request);

		ITextRenderer renderer = new ITextRenderer();
		renderer.setDocumentFromString(page);
		renderer.layout();
		return renderer;
	}

	private static byte[] createQRCode(String text) throws WriterException, IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Map<EncodeHintType, Object> hintMap = new EnumMap<>(EncodeHintType.class);
		hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
		BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 200, 200, hintMap);
		MatrixToImageWriter.writeToStream(matrix, "png", baos);
		return baos.toByteArray();
	}
}
