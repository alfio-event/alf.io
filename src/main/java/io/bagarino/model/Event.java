/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.model;

import io.bagarino.datamapper.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Event {
    private final int id;
    private final String description;
    private final String latitude;
    private final String longitude;
    private final LocalDateTime begin;
    private final LocalDateTime end;

    public Event(@Column("id") int id,
                 @Column("description") String description,
                 @Column("latitude") String latitude,
                 @Column("longitude") String longitude,
                 @Column("begin") LocalDateTime begin,
                 @Column("end") LocalDateTime end) {
        this.id = id;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.begin = begin;
        this.end = end;
    }
}
