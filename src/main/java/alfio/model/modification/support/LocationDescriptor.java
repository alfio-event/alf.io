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
package alfio.model.modification.support;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.text.StrSubstitutor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

@Getter
@EqualsAndHashCode
public class LocationDescriptor {

    private static final String MAP_URL = "https://maps.googleapis.com/maps/api/staticmap?center=${latitude},${longitude}&key=${key}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";
    private static final String OPENSTREETMAP = "https://tyler-demo.herokuapp.com/?center=${latitude},${longitude}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";

    private final String timeZone;
    private final String latitude;
    private final String longitude;
    private final String mapUrl;

    @JsonCreator
    public LocationDescriptor(@JsonProperty("timeZone") String timeZone,
                              @JsonProperty("latitude") String latitude,
                              @JsonProperty("longitude") String longitude,
                              @JsonProperty("mapUrl") String mapUrl) {
        this.timeZone = timeZone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mapUrl = mapUrl;
    }

    public static LocationDescriptor fromGeoData(Pair<String, String> coordinates, TimeZone timeZone, Optional<String> apiKey) {
        Map<String, String> params = new HashMap<>();
        params.put("latitude", coordinates.getLeft());
        params.put("longitude", coordinates.getRight());
        apiKey.ifPresent((key) -> params.put("key", key));
        return new LocationDescriptor(timeZone.getID(), coordinates.getLeft(), coordinates.getRight(), new StrSubstitutor(params).replace(apiKey.isPresent() ? MAP_URL : OPENSTREETMAP));
    }

}
