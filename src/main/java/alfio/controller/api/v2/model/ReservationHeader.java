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
package alfio.controller.api.v2.model;

import alfio.model.PriceContainer;
import alfio.model.ReservationWithPurchaseContext;
import alfio.model.TicketReservation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static alfio.util.LocaleUtil.formatDate;

@RequiredArgsConstructor
@Getter
public class ReservationHeader {
    private final String id;
    private final TicketReservation.TicketReservationStatus status;
    private final Map<String, String> formattedExpiresOn;
    private final Map<String, String> formattedConfirmedOn;
    private final Map<String, String> formattedCreatedOn;
    private final String invoiceNumber;
    private final BigDecimal finalPrice;
    private final String currencyCode;
    private final BigDecimal usedVatPercent;
    private final PriceContainer.VatStatus vatStatus;
    private final List<ReservationWithPurchaseContext.PurchaseContextItem> items;


    public static ReservationHeader from(ReservationWithPurchaseContext r, Map<Locale, String> datePatternsMap) {
        return new ReservationHeader(
            r.getId(),
            r.getStatus(),
            formatDate(r.getValidity(), datePatternsMap),
            formatDate(r.getConfirmationTs(), datePatternsMap),
            formatDate(r.getRegistrationTs(), datePatternsMap),
            r.getInvoiceNumber(),
            r.getFinalPrice(),
            r.getCurrencyCode(),
            r.getVatPercentage(),
            r.getVatStatus(),
            r.getItems()
        );
    }
}
