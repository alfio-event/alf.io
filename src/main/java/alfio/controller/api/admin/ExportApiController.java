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
package alfio.controller.api.admin;

import alfio.manager.ExportManager;
import alfio.model.ReservationsByEvent;
import alfio.model.support.ReservationInfo;
import alfio.model.support.TicketInfo;
import alfio.util.MonetaryUtil;
import ch.digitalfondue.basicxlsx.StreamingWorkbook;
import ch.digitalfondue.basicxlsx.Style;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static alfio.util.ExportUtils.addSheetToWorkbook;
import static alfio.util.ExportUtils.exportExcel;
import static java.util.Objects.*;

@RestController
@RequestMapping("/admin/api/export")
public class ExportApiController {

    private final ExportManager exportManager;

    public ExportApiController(ExportManager exportManager) {
        this.exportManager = exportManager;
    }

    @GetMapping("/reservations")
    public void downloadAllEvents(@RequestParam(name = "from") String from,
                                  @RequestParam(name = "to") String to,
                                  HttpServletResponse response,
                                  Principal principal) throws IOException {
        var allEvents = exportManager.reservationsForInterval(LocalDate.parse(requireNonNull(from)),
            LocalDate.parse(requireNonNull(to)), requireNonNull(principal));
        if (allEvents.isEmpty()) {
            response.setContentType("text/plain");
            response.setStatus(HttpStatus.PRECONDITION_REQUIRED.value());
            response.getWriter().write("No reservations found for the selected period");
        } else {
            exportExcel("all-reservations.xlsx", response, workbook -> writeSheets(allEvents, workbook));
        }
    }

    private static void writeSheets(List<ReservationsByEvent> allEvents, StreamingWorkbook workbook) {
        var header = new String[] {
            "Event Name",
            "Reservation ID",
            "Confirmation Date",
            "Billed to",
            "Tax ID",
            "Tax Code",
            "Invoice #",
            "Amount",
            "Tax",
            "Currency",
            "Payment Type",
            "Ticket ID",
            "Ticket Type",
            "Ticket Amount",
            "Ticket Tax",
            "Attendee",
            "Status"
        };
        var headerStyle = workbook.defineStyle().font().bold(true).build();
        allEvents.stream().sorted(Comparator.comparing(ReservationsByEvent::getEventShortName))
            .forEach(e -> addSheet(workbook, header, headerStyle, e));
    }

    private static void addSheet(StreamingWorkbook workbook, String[] header, Style headerStyle, ReservationsByEvent eventWithReservations) {
        var rowData = eventWithReservations.getReservations().stream()
                .sorted(Comparator.comparing(ReservationInfo::getConfirmationTimestamp))
                .flatMap(r -> ticketRows(eventWithReservations, r));
        addSheetToWorkbook(eventWithReservations.getEventShortName(), header, rowData, workbook, headerStyle);
    }

    private static Stream<String[]> ticketRows(ReservationsByEvent eventWithReservations, ReservationInfo r) {
        return r.getTickets().stream()
            .map(t -> buildTicketRow(eventWithReservations, r, t));
    }

    private static String[] buildTicketRow(ReservationsByEvent eventWithReservations,
                                           ReservationInfo r,
                                           TicketInfo t) {
        return new String[]{
            eventWithReservations.getDisplayName(),
            r.getId(),
            r.getConfirmationTimestamp(),
            billingCompanyOrFullName(r),
            r.getTaxId(),
            r.getTaxCode(),
            r.getInvoiceNumber(),
            formatAmount(r.getSrcPriceCts(), r.getCurrency()),
            formatAmount(r.getTaxCts(), r.getCurrency()),
            r.getCurrency(),
            r.getPaymentType().name(),
            t.getId(),
            t.getType(),
            formatAmount(t.getSrcPriceCts(), r.getCurrency()),
            formatAmount(t.getTaxCts(), r.getCurrency()),
            (requireNonNullElse(t.getFirstName(), "") + " " + requireNonNullElse(t.getLastName(), "")).trim(),
            t.getStatus()
        };
    }

    private static String billingCompanyOrFullName(ReservationInfo r) {
        return requireNonNullElseGet(r.getCompanyName(), () -> r.getFirstName() + " " + r.getLastName());
    }

    private static String formatAmount(Integer originalCts, String currency) {
        if (originalCts == null) {
            return "";
        }
        return MonetaryUtil.formatCents(originalCts, currency);
    }
}
