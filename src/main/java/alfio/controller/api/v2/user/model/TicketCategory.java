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

import java.util.Map;

@AllArgsConstructor
public class TicketCategory {
    private final SaleableTicketCategory ticketCategory;
    private final Map<String, String> description;

    public int getId() {
        return ticketCategory.getId();
    }

    public String getName() {
        return ticketCategory.getName();
    }

    public int[] getAmountOfTickets() {
        return ticketCategory.getAmountOfTickets();
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public boolean isFree() {
        return ticketCategory.getFree();
    }
}
