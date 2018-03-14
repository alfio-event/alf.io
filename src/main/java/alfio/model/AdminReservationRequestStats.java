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
public class AdminReservationRequestStats {

    private final String requestId;
    private final long userId;
    private final long eventId;
    private final int countSuccess;
    private final int countPending;
    private final int countError;


    public AdminReservationRequestStats(@Column("request_id") String requestId,
                                        @Column("user_id") long userId,
                                        @Column("event_id") long eventId,
                                        @Column("count_success") int countSuccess,
                                        @Column("count_pending") int countPending,
                                        @Column("count_error") int countError) {
        this.requestId = requestId;
        this.userId = userId;
        this.eventId = eventId;
        this.countSuccess = countSuccess;
        this.countPending = countPending;
        this.countError = countError;
    }
}
