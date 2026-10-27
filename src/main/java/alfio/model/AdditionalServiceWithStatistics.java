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
package alfio.model;

import alfio.model.AdditionalServiceItem.AdditionalServiceItemStatus;
import alfio.model.decorator.AdditionalServicePriceContainer;
import alfio.util.MonetaryUtil;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

public record AdditionalServiceWithStatistics(int id,
                                              AdditionalService.AdditionalServiceType type,
                                              Map<String, String> title,
                                              boolean fixPrice,
                                              BigDecimal regularPrice,
                                              BigDecimal finalPrice,
                                              BigDecimal taxablePrice,
                                              BigDecimal netPrice,
                                              BigDecimal vat,
                                              BigDecimal vatPercentage,
                                              PriceContainer.VatStatus vatStatus,
                                              Integer availableQuantity,
                                              int sold,
                                              int acquired,
                                              int checkedIn,
                                              int toBePaid,
                                              int pending,
                                              Integer remaining) {

    public static AdditionalServiceWithStatistics from(AdditionalService additionalService,
                                                       Event event,
                                                       Map<String, String> title,
                                                       Map<AdditionalServiceItemStatus, Integer> countByStatus) {
        boolean limited = additionalService.availableQuantity() > 0;
        int acquired = count(countByStatus, AdditionalServiceItemStatus.ACQUIRED);
        int checkedIn = count(countByStatus, AdditionalServiceItemStatus.CHECKED_IN);
        int toBePaid = count(countByStatus, AdditionalServiceItemStatus.TO_BE_PAID);
        Integer availableQuantity = limited ? additionalService.availableQuantity() : null;
        Integer remaining = limited ? count(countByStatus, AdditionalServiceItemStatus.FREE) : null;
        int pending = count(countByStatus, AdditionalServiceItemStatus.PENDING);
        if (!additionalService.fixPrice()) {
            // variable amount: the price is chosen by the attendee
            return new AdditionalServiceWithStatistics(additionalService.id(), additionalService.type(), title, false,
                null, null, null, null, null, null, null,
                availableQuantity, acquired + checkedIn + toBePaid, acquired, checkedIn, toBePaid, pending, remaining);
        }
        var priceContainer = AdditionalServicePriceContainer.from(null, additionalService, event, null);
        return new AdditionalServiceWithStatistics(additionalService.id(), additionalService.type(), title, true,
            MonetaryUtil.centsToUnit(priceContainer.getSrcPriceCts(), event.getCurrency()),
            priceContainer.getFinalPrice(),
            priceContainer.getTaxablePrice(),
            priceContainer.getNetPrice(),
            priceContainer.getVAT(),
            priceContainer.getVatPercentageOrZero(),
            priceContainer.getVatStatus(),
            availableQuantity, acquired + checkedIn + toBePaid, acquired, checkedIn, toBePaid, pending, remaining);
    }

    private static int count(Map<AdditionalServiceItemStatus, Integer> countByStatus, AdditionalServiceItemStatus status) {
        return Objects.requireNonNullElse(countByStatus.get(status), 0);
    }
}
