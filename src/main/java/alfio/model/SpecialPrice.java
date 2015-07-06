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
public class SpecialPrice {

    public enum Status {
        WAITING, FREE, PENDING, TAKEN, CANCELLED
    }

    private final int id;
    private final String code;
    private final int priceInCents;
    private final int ticketCategoryId;
    private final Status status;
    private final String sessionIdentifier;

    public SpecialPrice(@Column("id") int id,
                        @Column("code") String code,
                        @Column("price_cts") int priceInCents,
                        @Column("ticket_category_id") int ticketCategoryId,
                        @Column("status") String status,
                        @Column("session_id") String sessionIdentifier) {
        this.id = id;
        this.code = code;
        this.priceInCents = priceInCents;
        this.ticketCategoryId = ticketCategoryId;
        this.status = Status.valueOf(status);
        this.sessionIdentifier = sessionIdentifier;
    }
}
