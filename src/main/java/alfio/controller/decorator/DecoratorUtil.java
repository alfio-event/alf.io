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
package alfio.controller.decorator;

import alfio.model.PromoCodeDiscount;
import alfio.util.MonetaryUtil;

import java.math.BigDecimal;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;

class DecoratorUtil {
    static int[] generateRangeOfTicketQuantity(int maxTickets, int availableTickets) {
        final int maximumSaleableTickets = max(0, min(maxTickets, availableTickets));
        return IntStream.rangeClosed(0, maximumSaleableTickets).toArray();
    }

    static int calcDiscount(PromoCodeDiscount d, int finalPriceInCents) {
        int discount;
        if(d.getFixedAmount()) {
            discount = d.getDiscountAmount();
        } else {
            discount = MonetaryUtil.calcPercentage(finalPriceInCents, new BigDecimal(d.getDiscountAmount()));
        }
        return finalPriceInCents - discount;
    }
}
