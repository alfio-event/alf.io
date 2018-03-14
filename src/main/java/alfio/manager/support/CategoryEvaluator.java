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
package alfio.manager.support;

import alfio.model.Ticket;
import alfio.repository.TicketCategoryRepository;

import java.util.function.Function;

public final class CategoryEvaluator {
    private CategoryEvaluator() {
    }

    public static Function<Ticket, Boolean> ticketCancellationAvailabilityChecker(TicketCategoryRepository ticketCategoryRepository) {
        return ticket -> ticket.getStatus() == Ticket.TicketStatus.ACQUIRED && (!ticketCategoryRepository.getByIdAndActive(ticket.getCategoryId(), ticket.getEventId()).isAccessRestricted()
                         || ticketCategoryRepository.countUnboundedCategoriesByEventId(ticket.getEventId()) > 0);
    }

    public static boolean isTicketCancellationAvailable(TicketCategoryRepository ticketCategoryRepository, Ticket ticket) {
        return ticketCancellationAvailabilityChecker(ticketCategoryRepository).apply(ticket);
    }
}
