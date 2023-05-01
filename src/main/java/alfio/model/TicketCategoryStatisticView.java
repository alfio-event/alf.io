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

import alfio.model.system.Configuration;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;

@Getter
public class TicketCategoryStatisticView {


    private final int id;
    private final int maxTickets;
    private final boolean bounded;
    private final boolean expired;
    private final int eventId;
    private final int pendingCount;
    private final int checkedInCount;
    private final int soldTicketsCount;
    private final int notSoldTicketsCount;
    private final int releasedTicketsCount;
    private final int stuckCount;
    private final boolean containsOrphanTickets;
    private final boolean containsStuckTickets;
    private final List<Configuration> configuration;

    public TicketCategoryStatisticView(@Column("ticket_category_id") int id,
                                       @Column("max_tickets") int maxTickets,
                                       @Column("bounded") boolean bounded,
                                       @Column("is_expired") boolean expired,
                                       @Column("event_id") int eventId,
                                       @Column("pending_count") int pendingCount,
                                       @Column("checked_in_count") int checkedInCount,
                                       @Column("sold_tickets_count") int soldTicketsCount,
                                       @Column("not_sold_tickets") int notSoldTicketsCount,
                                       @Column("released_count") int releasedCount,
                                       @Column("stuck_count") int stuckCount,
                                       @Column("is_containing_orphan_tickets") boolean containsOrphanTickets,
                                       @Column("is_containing_stuck_tickets") boolean containsStuckTickets,
                                       @Column("category_configuration") String configurationJson) {
        this.id = id;
        this.maxTickets = maxTickets;
        this.bounded = bounded;
        this.expired = expired;
        this.eventId = eventId;
        this.pendingCount = pendingCount;
        this.checkedInCount = checkedInCount;
        this.soldTicketsCount = soldTicketsCount;
        this.notSoldTicketsCount = notSoldTicketsCount;
        this.stuckCount = stuckCount;
        this.containsOrphanTickets = containsOrphanTickets;
        this.containsStuckTickets = containsStuckTickets;
        this.releasedTicketsCount = releasedCount;
        this.configuration = Json.fromJson(Objects.requireNonNullElse(configurationJson, "[]"), new TypeReference<>() {});
    }

    public static TicketCategoryStatisticView empty(int ticketCategoryId, int eventId) {
        return new TicketCategoryStatisticView(ticketCategoryId, 0, false, false, eventId, 0, 0, 0, 0, 0, 0, false, false, null);
    }
}
