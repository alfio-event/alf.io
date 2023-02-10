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
public class TicketInfo {

    private final int ticketId;
    private final String ticketUuid;
    private final int ticketCategoryId;
    private final boolean ticketCategoryBounded;
    private final PriceContainer.VatStatus taxPolicy;


    public TicketInfo(@Column("t_id") int id,
                      @Column("t_uuid") String ticketUuid,
                      @Column("tc_id") int tcId,
                      @Column("tc_bounded") boolean bounded,
                      @Column("t_vat_status") PriceContainer.VatStatus taxPolicy) {
        this.ticketId = id;
        this.ticketUuid = ticketUuid;
        this.ticketCategoryId = tcId;
        this.ticketCategoryBounded = bounded;
        this.taxPolicy = taxPolicy;
    }
}
