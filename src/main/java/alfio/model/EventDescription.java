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
public class EventDescription {

    //
    public enum EventDescriptionType {
        DESCRIPTION
    }

    private final Integer eventId;
    private final String locale;
    private final EventDescriptionType eventDescriptionType;
    private final String description;

    public EventDescription(@Column("event_id_fk") Integer eventId, @Column("locale") String locale,
                            @Column("type") EventDescriptionType eventDescriptionType,
                            @Column("description") String description) {
        this.eventId = eventId;
        this.locale = locale;
        this.eventDescriptionType = eventDescriptionType;
        this.description = description;
    }

    @Getter
    public static class LocaleDescription {
        private final String locale;
        private final String description;

        public LocaleDescription(@Column("locale") String locale, @Column("description") String description) {
            this.locale = locale;
            this.description = description;
        }
    }
}
