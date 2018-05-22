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

import alfio.controller.support.TemplateProcessor;
import alfio.manager.FileUploadManager;
import alfio.manager.TicketReservationManager;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.TicketReservation;
import alfio.repository.EventRepository;
import alfio.util.Json;
import alfio.util.TemplateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@Controller
public class InvoiceReceiptController {

    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;
    private final FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;

    @Autowired
    public InvoiceReceiptController(EventRepository eventRepository,
                                    TicketReservationManager ticketReservationManager,
                                    FileUploadManager fileUploadManager,
                                    TemplateManager templateManager) {
        this.eventRepository = eventRepository;
        this.ticketReservationManager = ticketReservationManager;
        this.fileUploadManager = fileUploadManager;
        this.templateManager = templateManager;
    }

    private ResponseEntity<Void> handleReservationWith(String eventName, String reservationId, BiFunction<Event, TicketReservation, ResponseEntity<Void>> with) {
        ResponseEntity<Void> notFound = ResponseEntity.notFound().build();
        return eventRepository.findOptionalByShortName(eventName).map(event ->
            ticketReservationManager.findById(reservationId).map(ticketReservation ->
                with.apply(event, ticketReservation)).orElse(notFound)
        ).orElse(notFound);
    }

    private Optional<byte[]> buildDocument(Event event, TicketReservation reservation, Function<Map<String, Object>, Optional<byte[]>> documentGenerator) {
        OrderSummary orderSummary = Json.fromJson(reservation.getInvoiceModel(), OrderSummary.class);
        Optional<String> vat = Optional.ofNullable(orderSummary.getVatPercentage());
        Map<String, Object> reservationModel = ticketReservationManager.prepareModelForReservationEmail(event, reservation, vat, orderSummary);
        return documentGenerator.apply(reservationModel);
    }

    private static boolean sendPdf(Optional<byte[]> res, HttpServletResponse response, String eventName, String reservationId, String type) {
        return res.map(pdf -> {
            try {
                response.setHeader("Content-Disposition", "attachment; filename=\"" + type+  "-" + eventName + "-" + reservationId + ".pdf\"");
                response.setContentType("application/pdf");
                response.getOutputStream().write(pdf);
                return true;
            } catch(IOException e) {
                return false;
            }
        }).orElse(false);
    }

    @RequestMapping("/event/{eventName}/reservation/{reservationId}/receipt")
    public ResponseEntity<Void> getReceipt(@PathVariable("eventName") String eventName,
                                     @PathVariable("reservationId") String reservationId,
                                     HttpServletResponse response) {
        return handleReservationWith(eventName, reservationId, (event, reservation) -> {
            if(reservation.getInvoiceNumber() != null || !reservation.getHasInvoiceOrReceiptDocument() || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            Optional<byte[]> res = buildDocument(event, reservation, model -> TemplateProcessor.buildReceiptPdf(event, fileUploadManager, new Locale(reservation.getUserLanguage()), templateManager, model));
            boolean success = sendPdf(res, response, eventName, reservationId, "receipt");
            return success ? ResponseEntity.ok(null) : ResponseEntity.<Void>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        });
    }

    @RequestMapping("/event/{eventName}/reservation/{reservationId}/invoice")
    public void getInvoice(@PathVariable("eventName") String eventName,
                           @PathVariable("reservationId") String reservationId,
                           HttpServletResponse response) {
        handleReservationWith(eventName, reservationId, (event, reservation) -> {
            if(reservation.getInvoiceNumber() == null || !reservation.getHasInvoiceOrReceiptDocument() || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            Optional<byte[]> res = buildDocument(event, reservation, model -> TemplateProcessor.buildInvoicePdf(event, fileUploadManager, new Locale(reservation.getUserLanguage()), templateManager, model));
            boolean success = sendPdf(res, response, eventName, reservationId, "invoice");
            return success ? ResponseEntity.ok(null) : ResponseEntity.<Void>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        });
    }
}
