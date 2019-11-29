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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class ItemsByCategory {

    private final List<TicketCategory> ticketCategories;
    private final List<TicketCategory> expiredCategories;
    private final List<AdditionalService> additionalServices;

    private final boolean waitingList;
    private final boolean preSales;
    private final List<TicketCategoryForWaitingList> ticketCategoriesForWaitingList;


    @AllArgsConstructor
    @Getter
    public static class TicketCategoryForWaitingList {
        private final int id;
        private final String name;
    }
}
