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


import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

@Getter
public class UploadedResource {

    private final String name;
    private final Integer organizationId;
    private final Integer eventId;
    private final String contentType;
    private final int contentSize;
    private final Date creationTime;
    private final Map<String, String> attributes;

    public UploadedResource(@Column("name") String name,
                            @Column("organization_id_fk") Integer organizationId,
                            @Column("event_id_fk") Integer eventId,
                            @Column("content_type") String contentType,
                            @Column("content_size") int contentSize,
                            @Column("creation_time") Date creationTime,
                            @Column("attributes") String attributes) {
        this.name = name;
        this.organizationId = organizationId;
        this.eventId = eventId;
        this.contentType = contentType;
        this.contentSize = contentSize;
        this.creationTime = creationTime;
        Map<String, String> parsed = Json.GSON.fromJson(attributes, new TypeToken<Map<String, String>>() {}.getType());
        this.attributes = parsed != null ? parsed : Collections.emptyMap();
    }
}
