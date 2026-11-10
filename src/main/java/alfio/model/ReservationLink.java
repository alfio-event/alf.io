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

import java.time.ZonedDateTime;

@Getter
public class ReservationLink {

    public enum Status {
        ACTIVE, USED, REVOKED
    }

    private final int id;
    private final String token;
    private final int eventId;
    private final int categoryId;
    private final int quantity;
    private final ZonedDateTime expiresAt;
    private final Status status;
    private final ZonedDateTime createdAt;

    public ReservationLink(@Column("id") int id,
                           @Column("token") String token,
                           @Column("event_id") int eventId,
                           @Column("category_id") int categoryId,
                           @Column("quantity") int quantity,
                           @Column("expires_at") ZonedDateTime expiresAt,
                           @Column("status") String status,
                           @Column("created_at") ZonedDateTime createdAt) {
        this.id = id;
        this.token = token;
        this.eventId = eventId;
        this.categoryId = categoryId;
        this.quantity = quantity;
        this.expiresAt = expiresAt;
        this.status = Status.valueOf(status);
        this.createdAt = createdAt;
    }
}
