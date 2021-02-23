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
package alfio.util;

import alfio.model.BillingDocument;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

public class FileUtil {
    public static boolean sendPdf(byte[] res, HttpServletResponse response, String eventName, String reservationId, BillingDocument billingDocument) {
        return Optional.ofNullable(res).map(pdf -> {
            try {
                sendHeaders(response, eventName, reservationId, billingDocument);
                response.getOutputStream().write(pdf);
                return true;
            } catch(IOException e) {
                return false;
            }
        }).orElse(false);
    }


    public static void sendHeaders(HttpServletResponse response, String eventName, String reservationId, BillingDocument billingDocument) {
        response.setHeader("Content-Disposition", "attachment; filename=\"" + getBillingDocumentFileName(eventName, reservationId, billingDocument) + "\"");
        response.setContentType("application/pdf");
    }

    public static String getBillingDocumentFileName(String eventShortName, String reservationId, BillingDocument document) {
        if(document.getType() != BillingDocument.Type.RECEIPT) {
            Map<String, Object> reservationModel = document.getModel();
            ZonedDateTime invoiceDate = ZonedDateTime.parse((String) reservationModel.get("confirmationDate"));
            String formattedDate = invoiceDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"));
            return eventShortName +
                "-" + formattedDate +
                "-" + document.getNumber() +
                "-" + document.getId()+
                ".pdf";
        } else {
            return "receipt-" + eventShortName + "-" + reservationId + ".pdf";
        }
    }

}
