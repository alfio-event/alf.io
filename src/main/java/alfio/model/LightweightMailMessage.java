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

import java.time.ZonedDateTime;

public class LightweightMailMessage extends EmailMessage {
    public LightweightMailMessage(@Column("id") int id,
                        @Column("event_id") int eventId,
                        @Column("status") String status,
                        @Column("recipient") String recipient,
                        @Column("subject") String subject,
                        @Column("message") String message,
                        @Column("checksum") String checksum,
                        @Column("request_ts") ZonedDateTime requestTimestamp,
                        @Column("sent_ts") ZonedDateTime sentTimestamp,
                        @Column("attempts") int attempts) {
        super(id, eventId, status, recipient, subject, message, null, checksum, requestTimestamp, sentTimestamp, attempts);
    }
}
