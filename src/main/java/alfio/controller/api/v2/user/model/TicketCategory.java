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
package alfio.controller.api.v2.user.model;

import alfio.controller.decorator.SaleableTicketCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
public class TicketCategory {

    private final Map<String, String> description;
    private final int id;
    private final String name;
    private final int[] amountOfTickets;
    private final boolean free;

    public TicketCategory(SaleableTicketCategory saleableTicketCategory, Map<String, String> description) {
        this.description = description;
        this.id = saleableTicketCategory.getId();
        this.name = saleableTicketCategory.getName();
        this.amountOfTickets = saleableTicketCategory.getAmountOfTickets();
        this.free = saleableTicketCategory.getFree();
    }
}
