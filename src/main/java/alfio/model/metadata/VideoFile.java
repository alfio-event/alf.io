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
package alfio.model.metadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class VideoFile implements Comparable<VideoFile> {
    private String link;
    private String name;
    private String previewUrl;
    private LocalDateTime date;


    @JsonCreator
    public VideoFile(@JsonProperty("link") String link,
                     @JsonProperty("name") String name,
                     @JsonProperty("previewUrl") String previewUrl,
                     @JsonProperty("date") LocalDateTime date) {
        this.link = link;
        this.name = name;
        this.date = date;
        this.previewUrl = previewUrl;
    }

    public int compareTo(VideoFile o) {
        LocalDateTime u = o.getDate();
        return date.isBefore(u) ? -1 : date.isEqual(u) ? 0 : 1;
    }
}
