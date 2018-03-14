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

import alfio.model.system.ConfigurationKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StrSubstitutor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

@Getter
@EqualsAndHashCode
public class LocationDescriptor {


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

    public static LocationDescriptor fromGeoData(Pair<String, String> coordinates, TimeZone timeZone, Map<ConfigurationKeys, Optional<String>> geoConf) {
        Map<String, String> params = new HashMap<>();
        String lat = coordinates.getLeft();
        String lng = coordinates.getRight();
        params.put("latitude", lat);
        params.put("longitude", lng);

        return new LocationDescriptor(timeZone.getID(), coordinates.getLeft(), coordinates.getRight(), getMapUrl(lat, lng, geoConf));
    }

    public static String getMapUrl(String lat, String lng, Map<ConfigurationKeys, Optional<String>> geoConf) {
        Map<String, String> params = new HashMap<>();
        params.put("latitude", lat);
        params.put("longitude", lng);

        ConfigurationKeys.GeoInfoProvider provider = getProvider(geoConf);
        String mapUrl = mapUrl(provider);

        fillParams(provider, geoConf, params);

        return new StrSubstitutor(params).replace(mapUrl);
    }

    // for backward compatibility reason, the logic is not straightforward
    public static ConfigurationKeys.GeoInfoProvider getProvider(Map<ConfigurationKeys, Optional<String>> geoConf) {
        if((!geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) || !geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isPresent()) &&
            (geoConf.containsKey(ConfigurationKeys.MAPS_CLIENT_API_KEY) && geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).isPresent())) {
            return ConfigurationKeys.GeoInfoProvider.GOOGLE;
        } else if (geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) && geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isPresent()) {
            return geoConf.get(ConfigurationKeys.MAPS_PROVIDER).map(ConfigurationKeys.GeoInfoProvider::valueOf).orElseThrow(IllegalStateException::new);
        } else {
            return ConfigurationKeys.GeoInfoProvider.NONE;
        }
    }

    private static String mapUrl(ConfigurationKeys.GeoInfoProvider provider) {
        switch (provider) {
            case GOOGLE: return "https://maps.googleapis.com/maps/api/staticmap?center=${latitude},${longitude}&key=${key}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";
            case HERE: return "https://image.maps.api.here.com/mia/1.6/mapview?c=${latitude},${longitude}&z=16&w=400&h=400&poi=${latitude},${longitude}&app_id=${appId}&app_code=${appCode}";
            default: return "https://tyler-demo.herokuapp.com/?center=${latitude},${longitude}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";
        }
    }

    private static void fillParams(ConfigurationKeys.GeoInfoProvider provider, Map<ConfigurationKeys, Optional<String>> geoConf, Map<String, String> params) {
        if(ConfigurationKeys.GeoInfoProvider.GOOGLE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).ifPresent((key) -> params.put("key", key));
        } else if (ConfigurationKeys.GeoInfoProvider.HERE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_HERE_APP_ID).ifPresent((appId) -> params.put("appId", appId));
            geoConf.get(ConfigurationKeys.MAPS_HERE_APP_CODE).ifPresent((appCode) -> params.put("appCode", appCode));
        }
    }

}
