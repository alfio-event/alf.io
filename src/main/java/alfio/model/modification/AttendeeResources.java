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
package alfio.model.modification;

import alfio.manager.system.ConfigurationManager;
import alfio.model.PurchaseContext;
import alfio.model.Ticket;
import alfio.model.system.ConfigurationKeys;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import org.springframework.web.util.UriTemplate;

import java.util.Map;

import static alfio.controller.Constants.*;
import static alfio.model.system.ConfigurationKeys.*;

public record AttendeeResources(
    @JsonInclude(Include.NON_NULL) String ticketPdf,
    @JsonInclude(Include.NON_NULL) String ticketQrCode,
    @JsonInclude(Include.NON_NULL) String googleWallet,
    @JsonInclude(Include.NON_NULL) String applePass,
    @JsonInclude(Include.NON_NULL) Boolean ready
) {

    public static AttendeeResources empty() {
        return new AttendeeResources(null, null, null, null, null);
    }

    public static AttendeeResources fromTicket(Ticket ticket,
                                               PurchaseContext purchaseContext,
                                               Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> conf) {
        return fromTicket(ticket, purchaseContext, conf, false);
    }

    public static AttendeeResources fromTicket(Ticket ticket,
                                               PurchaseContext purchaseContext,
                                               Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> conf,
                                               boolean allowPendingTickets) {
        if (ticket.getStatus() == Ticket.TicketStatus.PENDING && !allowPendingTickets) {
            return AttendeeResources.empty();
        }
        var baseUrl = conf.get(BASE_URL).getRequiredValue();
        var ticketPdfUriTemplate = new UriTemplate(baseUrl + TICKET_PDF_URI);
        var walletUriTemplate = new UriTemplate(baseUrl + WALLET_API_BASE_URI + WALLET_API_GET_URI);
        var passUriTemplate = new UriTemplate(baseUrl + PASS_API_BASE_URI + WALLET_API_GET_URI);
        var qrCodeTemplate = new UriTemplate(baseUrl + TICKET_QR_CODE_URI);
        return new AttendeeResources(
                expandUriTemplate(ticketPdfUriTemplate, purchaseContext, ticket),
                expandUriTemplate(qrCodeTemplate, purchaseContext, ticket),
                generateGoogleWalletUrl(walletUriTemplate, conf, purchaseContext, ticket),
                generatePasskitUrl(passUriTemplate, conf, purchaseContext, ticket),
                ticket.getAssigned()
        );
    }

    private static String generateGoogleWalletUrl(UriTemplate walletUriTemplate,
                                                  Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> conf,
                                                  PurchaseContext purchaseContext,
                                                  Ticket ticket) {
        if (conf.get(ENABLE_WALLET).getValueAsBooleanOrDefault()) {
            return expandUriTemplate(walletUriTemplate, purchaseContext, ticket);
        }
        return null;
    }

    private static String generatePasskitUrl(UriTemplate passkitUriTemplate,
                                             Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> conf,
                                             PurchaseContext purchaseContext,
                                             Ticket ticket) {
        if (conf.get(ENABLE_PASS).getValueAsBooleanOrDefault()) {
            return expandUriTemplate(passkitUriTemplate, purchaseContext, ticket);
        }
        return null;
    }

    private static String expandUriTemplate(UriTemplate template, PurchaseContext purchaseContext, Ticket ticket) {
        return template.expand(purchaseContext.getPublicIdentifier(), ticket.getPublicUuid().toString())
            .toString();
    }
}
