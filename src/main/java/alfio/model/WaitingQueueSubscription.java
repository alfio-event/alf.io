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

import alfio.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Locale;

@Getter
public class WaitingQueueSubscription {

    public enum Status {
        WAITING, ACQUIRED, EXPIRED
    }

    private final int id;
    private final ZonedDateTime creation;
    private final int eventId;
    private final Status status;
    private final String fullName;
    private final String emailAddress;
    private final String userLanguage;

    public WaitingQueueSubscription(@Column("id") int id,
                                    @Column("creation") ZonedDateTime creation,
                                    @Column("event_id") int eventId,
                                    @Column("status") String status,
                                    @Column("full_name") String fullName,
                                    @Column("email_address") String emailAddress,
                                    @Column("user_language") String userLanguage) {
        this.id = id;
        this.creation = creation;
        this.eventId = eventId;
        this.userLanguage = userLanguage;
        this.status = Status.valueOf(status);
        this.fullName = fullName;
        this.emailAddress = emailAddress;
    }

    public Locale getLocale() {
        return Locale.forLanguageTag(userLanguage);
    }
}
