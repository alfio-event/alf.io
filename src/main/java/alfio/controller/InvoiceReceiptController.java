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
import alfio.manager.ExtensionManager;
import alfio.manager.FileUploadManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.OrderSummary;
import alfio.model.TicketReservation;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.repository.EventRepository;
import alfio.util.FileUtil;
import alfio.util.Json;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

@Controller
@AllArgsConstructor
public class InvoiceReceiptController {

    private final EventRepository eventRepository;
    private final TicketReservationManager ticketReservationManager;
    private final FileUploadManager fileUploadManager;
    private final TemplateManager templateManager;
    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;

    private ResponseEntity<Void> handleReservationWith(String eventName, String reservationId, BiFunction<Event, TicketReservation, ResponseEntity<Void>> with) {
        ResponseEntity<Void> notFound = ResponseEntity.notFound().build();
        return eventRepository.findOptionalByShortName(eventName).map(event ->
            ticketReservationManager.findById(reservationId).map(ticketReservation ->
                with.apply(event, ticketReservation)).orElse(notFound)
        ).orElse(notFound);
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null ||
            authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ANONYMOUS"::equals);
    }

    @RequestMapping("/event/{eventName}/reservation/{reservationId}/receipt")
    public ResponseEntity<Void> getReceipt(@PathVariable("eventName") String eventName,
                                           @PathVariable("reservationId") String reservationId,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        return handleReservationWith(eventName, reservationId, (event, reservation) -> {
            if(reservation.getInvoiceNumber() != null || !reservation.getHasInvoiceOrReceiptDocument() || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            if (!configurationManager.canGenerateReceiptOrInvoiceToCustomer(event) && isAnonymous(authentication)) {
                return ResponseEntity.badRequest().build();
            }

            return handleReservationWith(eventName, reservationId, generatePdfFunction(false, response));
        });
    }

    @RequestMapping("/event/{eventName}/reservation/{reservationId}/invoice")
    public ResponseEntity<Void> getInvoice(@PathVariable("eventName") String eventName,
                                           @PathVariable("reservationId") String reservationId,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        return handleReservationWith(eventName, reservationId, (event, reservation) -> {
            if(reservation.getInvoiceNumber() == null || !reservation.getHasInvoiceOrReceiptDocument() || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            if (!configurationManager.canGenerateReceiptOrInvoiceToCustomer(event) && isAnonymous(authentication)) {
                return ResponseEntity.badRequest().build();
            }

            return handleReservationWith(eventName, reservationId, generatePdfFunction(true, response));
        });
    }

    private BiFunction<Event, TicketReservation, ResponseEntity<Void>> generatePdfFunction(boolean forInvoice, HttpServletResponse response) {
        return (event, reservation) -> {
            if(forInvoice ^ reservation.getInvoiceNumber() != null || reservation.isCancelled()) {
                return ResponseEntity.notFound().build();
            }

            OrderSummary orderSummary = Json.fromJson(reservation.getInvoiceModel(), OrderSummary.class);
            Optional<String> vat = Optional.ofNullable(orderSummary.getVatPercentage());
            Map<String, Object> reservationModel = ticketReservationManager.prepareModelForReservationEmail(event, reservation, vat, orderSummary);

            try {
                FileUtil.sendHeaders(response, event.getShortName(), reservation.getId(), forInvoice ? "invoice" : "receipt");
                TemplateProcessor.buildReceiptOrInvoicePdf(event, fileUploadManager, new Locale(reservation.getUserLanguage()),
                    templateManager, reservationModel, forInvoice ? TemplateResource.INVOICE_PDF : TemplateResource.RECEIPT_PDF,
                    extensionManager, response.getOutputStream());
                return ResponseEntity.ok(null);
            } catch (IOException ioe) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
        };
    }
}
