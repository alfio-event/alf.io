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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class EventStatisticView {

    public EventStatisticView(@Column("is_containing_orphan_tickets") boolean containsOrphanTickets,
                              @Column("is_containing_stuck_tickets_count") boolean containsStuckReservations,
                              @Column("available_seats") int availableSeats,
                              @Column("not_sold_tickets") int notSoldTickets,
                              @Column("sold_tickets") int soldTickets,
                              @Column("checked_in_tickets") int checkedInTickets,
                              @Column("not_allocated_tickets") int notAllocatedTickets,
                              @Column("pending_tickets") int pendingTickets,
                              @Column("released_tickets") int releasedTickets,
                              @Column("dynamic_allocation") int dynamicAllocation,
                              @Column("id") int eventId) {
        this.eventId = eventId;
        this.containsOrphanTickets = containsOrphanTickets;
        this.containsStuckReservations = containsStuckReservations;
        this.availableSeats = availableSeats;
        this.notSoldTickets = notSoldTickets;
        this.soldTickets = soldTickets;
        this.checkedInTickets = checkedInTickets;
        this.notAllocatedTickets = notAllocatedTickets;
        this.pendingTickets = pendingTickets;
        this.releasedTickets = releasedTickets;
        this.dynamicAllocation = dynamicAllocation;
    }

    private final int eventId;
    private final boolean containsOrphanTickets;
    private final boolean containsStuckReservations;
    private final int availableSeats;
    private final int notSoldTickets;
    private final int soldTickets;
    private final int checkedInTickets;
    private final int notAllocatedTickets;
    private final int pendingTickets;
    private final int dynamicAllocation;
    private final int releasedTickets;

    public boolean isLiveData() {
        return true;
    }

    public static EventStatisticView empty(int eventId) {
        return new EventStatisticView(false, false, 0, 0, 0, 0, 0, 0, 0, 0, eventId) {
            @Override
            public boolean isLiveData() {
                return false;
            }
        };
    }

}
