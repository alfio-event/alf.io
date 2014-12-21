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
import alfio.manager.support.PDFTemplateBuilder;
import alfio.manager.support.TextTemplateBuilder;
import alfio.model.Event;
import alfio.model.Ticket;
import alfio.model.TicketCategory;
import alfio.model.TicketReservation;
import alfio.model.transaction.PaymentProxy;
import alfio.model.user.Organization;
import alfio.repository.user.OrganizationRepository;
import com.google.zxing.WriterException;
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

    public static TextTemplateBuilder buildEmail(Event event,
                                              OrganizationRepository organizationRepository,
                                              TicketReservation ticketReservation,
                                              Ticket ticket,
                                              TemplateManager templateManager,
                                              HttpServletRequest request) {
        return (() -> {
            Map<String, Object> model = new HashMap<>();
            model.put("organization", organizationRepository.getById(event.getOrganizationId()));
            model.put("event", event);
            model.put("ticketReservation", ticketReservation);
            model.put("ticket", ticket);
            return templateManager.render("/alfio/templates/ticket-email-txt.ms", model, request);
        });
    }

    public static TextTemplateBuilder buildEmailForOwnerChange(String newEmailOwner,
                                                            Event e,
                                                            Ticket t,
                                                            OrganizationRepository organizationRepository,
                                                            TicketReservationManager ticketReservationManager,
                                                            TemplateManager templateManager,
                                                            HttpServletRequest request) {
        return () -> {
            String eventName = e.getShortName();
            Map<String, Object> emailModel = new HashMap<>();
            emailModel.put("ticket", t);
            emailModel.put("organization", organizationRepository.getById(e.getOrganizationId()));
            emailModel.put("eventName", eventName);
            emailModel.put("previousEmail", t.getEmail());
            emailModel.put("newEmail", newEmailOwner);
            emailModel.put("reservationUrl", ticketReservationManager.reservationUrl(t.getTicketsReservationId()));
            return templateManager.render("/alfio/templates/ticket-has-changed-owner-txt.ms", emailModel, request);
        };
    }

    public static PDFTemplateBuilder buildPDFTicket(HttpServletRequest request,
                                                    Event event,
                                                    TicketReservation ticketReservation,
                                                    Ticket ticket,
                                                    TicketCategory ticketCategory,
                                                    Organization organization,
                                                    TemplateManager templateManager) throws WriterException, IOException {
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
            model.put("hasBeenPaid", Optional.ofNullable(ticketReservation.getPaymentMethod()).orElse(PaymentProxy.STRIPE) == PaymentProxy.STRIPE);

            String page = templateManager.render("/alfio/templates/ticket.ms", model, request);

            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(page);
            renderer.layout();
            return renderer;
        };
    }
}
