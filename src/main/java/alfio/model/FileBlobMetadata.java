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
import org.apache.commons.lang3.ObjectUtils;

import java.util.Collections;
import java.util.Map;

@Getter
public class FileBlobMetadata {

    public static final String ATTR_IMG_WIDTH = "width";
    public static final String ATTR_IMG_HEIGHT = "height";

    private final String id;
    private final String name;
    private final int contentSize;
    private final String contentType;
    private final Map<String, String> attributes;

    public FileBlobMetadata(@Column("id") String id,
                            @Column("name") String name,
                            @Column("content_size") int contentSize,
                            @Column("content_type") String contentType,
                            @Column("attributes") String attributes) {
        this.id = id;
        this.name = name;
        this.contentSize = contentSize;
        this.contentType = contentType;
        Map<String, String> parsed = Json.GSON.fromJson(attributes, new TypeToken<Map<String, String>>() {}.getType());
        this.attributes = ObjectUtils.firstNonNull(parsed, Collections.emptyMap());
    }

}
