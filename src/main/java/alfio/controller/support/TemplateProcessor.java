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
package alfio.controller.support;

import alfio.manager.TicketReservationManager;
import alfio.manager.support.PDFTemplateGenerator;
import alfio.manager.support.PartialTicketPDFGenerator;
import alfio.manager.support.PartialTicketTextGenerator;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.user.OrganizationRepository;
import alfio.util.TemplateManager;
import com.google.zxing.WriterException;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.xhtmlrenderer.pdf.ITextRenderer;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static alfio.util.TicketUtil.createQRCode;

public final class TemplateProcessor {

    private TemplateProcessor() {}

    public static PartialTicketTextGenerator buildPartialEmail(Event event,
                                                   OrganizationRepository organizationRepository,
                                                   TicketReservation ticketReservation,
                                                   TemplateManager templateManager,
                                                   HttpServletRequest request) {
        return (ticket) -> {
            Map<String, Object> model = new HashMap<>();
            model.put("organization", organizationRepository.getById(event.getOrganizationId()));
            model.put("event", event);
            model.put("ticketReservation", ticketReservation);
            model.put("ticket", ticket);
            return templateManager.renderClassPathResource("/alfio/templates/ticket-email-txt.ms", model, RequestContextUtils.getLocale(request));
        };
    }

    public static PartialTicketTextGenerator buildEmailForOwnerChange(Event e,
                                                                 Ticket oldTicket,
                                                                 OrganizationRepository organizationRepository,
                                                                 TicketReservationManager ticketReservationManager,
                                                                 TemplateManager templateManager,
                                                                 HttpServletRequest request) {
        return (newTicket) -> {
            String eventName = e.getShortName();
            Map<String, Object> emailModel = new HashMap<>();
            emailModel.put("ticket", oldTicket);
            emailModel.put("organization", organizationRepository.getById(e.getOrganizationId()));
            emailModel.put("eventName", eventName);
            emailModel.put("previousEmail", oldTicket.getEmail());
            emailModel.put("newEmail", newTicket.getEmail());
            emailModel.put("reservationUrl", ticketReservationManager.reservationUrl(oldTicket.getTicketsReservationId()));
            return templateManager.renderClassPathResource("/alfio/templates/ticket-has-changed-owner-txt.ms", emailModel, RequestContextUtils.getLocale(request));
        };
    }

    public static PDFTemplateGenerator buildPDFTicket(HttpServletRequest request,
                                                    Event event,
                                                    TicketReservation ticketReservation,
                                                    Ticket ticket,
                                                    TicketCategory ticketCategory,
                                                    Organization organization,
                                                    TemplateManager templateManager) {
        return () -> {
            String qrCodeText =  ticket.ticketCode(event.getPrivateKey());
            //
            Map<String, Object> model = new HashMap<>();
            model.put("ticket", ticket);
            model.put("reservation", ticketReservation);
            model.put("ticketCategory", ticketCategory);
            model.put("event", event);
            model.put("organization", organization);
            model.put("qrCodeDataUri", "data:image/png;base64," + Base64.getEncoder().encodeToString(createQRCode(qrCodeText)));
            model.put("deskPaymentRequired", Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE).isDeskPaymentRequired());

            String page = templateManager.renderClassPathResource("/alfio/templates/ticket.ms", model, RequestContextUtils.getLocale(request));

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(page);
            renderer.layout();
            return renderer;
        };
    }

    public static PartialTicketPDFGenerator buildPartialPDFTicket(HttpServletRequest request,
                                                                  Event event,
                                                                  TicketReservation ticketReservation,
                                                                  TicketCategory ticketCategory,
                                                                  Organization organization,
                                                                  TemplateManager templateManager) throws WriterException, IOException {
        return (ticket) -> buildPDFTicket(request, event, ticketReservation, ticket, ticketCategory, organization, templateManager).generate();
    }
}
