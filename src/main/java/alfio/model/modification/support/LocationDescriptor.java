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

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event.EventFormat;
import alfio.model.system.ConfigurationKeys;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringSubstitutor;

import java.util.HashMap;
import java.util.Map;
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

    public boolean getHasMapUrl() {
        return StringUtils.isNotBlank(mapUrl);
    }

    public static LocationDescriptor fromGeoData(EventFormat format, Pair<String, String> coordinates, TimeZone timeZone, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> geoConf) {
        if(format == EventFormat.ONLINE) {
            return new LocationDescriptor(timeZone.getID(), null, null, null);
        }
        String lat = coordinates.getLeft();
        String lng = coordinates.getRight();
        return new LocationDescriptor(timeZone.getID(), coordinates.getLeft(), coordinates.getRight(), getMapUrl(lat, lng, geoConf));
    }

    public static String getMapUrl(String lat, String lng, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> geoConf) {
        Map<String, String> params = new HashMap<>();
        params.put("latitude", lat);
        params.put("longitude", lng);

        ConfigurationKeys.GeoInfoProvider provider = getProvider(geoConf);
        String mapUrl = mapUrl(provider);

        fillParams(provider, geoConf, params);

        return new StringSubstitutor(params).replace(mapUrl);
    }

    // for backward compatibility reason, the logic is not straightforward
    public static ConfigurationKeys.GeoInfoProvider getProvider(Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> geoConf) {
        if((!geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) || geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isEmpty()) &&
            (geoConf.containsKey(ConfigurationKeys.MAPS_CLIENT_API_KEY) && geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).isPresent())) {
            return ConfigurationKeys.GeoInfoProvider.GOOGLE;
        } else if (geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) && geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isPresent()) {
            return geoConf.get(ConfigurationKeys.MAPS_PROVIDER).getValue().map(ConfigurationKeys.GeoInfoProvider::valueOf).orElseThrow(IllegalStateException::new);
        } else {
            return ConfigurationKeys.GeoInfoProvider.NONE;
        }
    }

    private static String mapUrl(ConfigurationKeys.GeoInfoProvider provider) {
        switch (provider) {
            case GOOGLE: return "https://maps.googleapis.com/maps/api/staticmap?center=${latitude},${longitude}&key=${key}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";
            case HERE: return "https://image.maps.ls.hereapi.com/mia/1.6/mapview?c=${latitude},${longitude}&z=16&w=400&h=400&poi=${latitude},${longitude}&apikey=${key}";
            default: return "";
        }
    }

    private static void fillParams(ConfigurationKeys.GeoInfoProvider provider, Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> geoConf, Map<String, String> params) {
        if(ConfigurationKeys.GeoInfoProvider.GOOGLE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).getValue().ifPresent(key -> params.put("key", key));
        } else if (ConfigurationKeys.GeoInfoProvider.HERE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_HERE_API_KEY).getValue().ifPresent(key -> params.put("key", key));
        }
    }

}
