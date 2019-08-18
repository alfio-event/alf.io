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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringSubstitutor;

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

    public boolean getHasMapUrl() {
        return StringUtils.isNotBlank(mapUrl);
    }

    public static LocationDescriptor fromGeoData(Pair<String, String> coordinates, TimeZone timeZone, Map<ConfigurationKeys, Optional<String>> geoConf) {
        String lat = coordinates.getLeft();
        String lng = coordinates.getRight();
        return new LocationDescriptor(timeZone.getID(), coordinates.getLeft(), coordinates.getRight(), getMapUrl(lat, lng, geoConf));
    }

    public static String getMapUrl(String lat, String lng, Map<ConfigurationKeys, Optional<String>> geoConf) {
        Map<String, String> params = new HashMap<>();
        params.put("latitude", lat);
        params.put("longitude", lng);
        ConfigurationKeys.GeoInfoProvider provider = getProvider(geoConf);
        String mapUrl = mapUrl(provider, lat, lng);
        fillParams(provider, geoConf, params);
        return new StringSubstitutor(params).replace(mapUrl);
    }

    // for backward compatibility reason, the logic is not straightforward
    public static ConfigurationKeys.GeoInfoProvider getProvider(Map<ConfigurationKeys, Optional<String>> geoConf) {
        if((!geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) || geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isEmpty()) &&
            (geoConf.containsKey(ConfigurationKeys.MAPS_CLIENT_API_KEY) && geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).isPresent())) {
            return ConfigurationKeys.GeoInfoProvider.GOOGLE;
        } else if (geoConf.containsKey(ConfigurationKeys.MAPS_PROVIDER) && geoConf.get(ConfigurationKeys.MAPS_PROVIDER).isPresent()) {
            return geoConf.get(ConfigurationKeys.MAPS_PROVIDER).map(ConfigurationKeys.GeoInfoProvider::valueOf).orElseThrow(IllegalStateException::new);
        } else {
            return ConfigurationKeys.GeoInfoProvider.NONE;
        }
    }

    private static String mapUrl(ConfigurationKeys.GeoInfoProvider provider, String lat, String lng) {
        switch (provider) {
            case GOOGLE: return "https://maps.googleapis.com/maps/api/staticmap?center=${latitude},${longitude}&key=${key}&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7C${latitude},${longitude}";
            case HERE: return "https://image.maps.api.here.com/mia/1.6/mapview?c=${latitude},${longitude}&z=16&w=400&h=400&poi=${latitude},${longitude}&app_id=${appId}&app_code=${appCode}";
            case GEOAPIFY: return "https://maps.geoapify.com/v1/tile/carto/"+getTileNumber(Double.parseDouble(lat), Double.parseDouble(lng), 18)+".png?apiKey=${key}";
            default: return "";
        }
    }

    // from https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Java
    public static String getTileNumber(final double lat, final double lon, final int zoom) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));
        if (xtile < 0)
            xtile = 0;
        if (xtile >= (1 << zoom))
            xtile = ((1 << zoom) - 1);
        if (ytile < 0)
            ytile = 0;
        if (ytile >= (1 << zoom))
            ytile = ((1 << zoom) - 1);
        return ("" + zoom + "/" + xtile + "/" + ytile);
    }

    private static void fillParams(ConfigurationKeys.GeoInfoProvider provider, Map<ConfigurationKeys, Optional<String>> geoConf, Map<String, String> params) {
        if(ConfigurationKeys.GeoInfoProvider.GOOGLE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_CLIENT_API_KEY).ifPresent(key -> params.put("key", key));
        } else if (ConfigurationKeys.GeoInfoProvider.HERE == provider) {
            geoConf.get(ConfigurationKeys.MAPS_HERE_APP_ID).ifPresent(appId -> params.put("appId", appId));
            geoConf.get(ConfigurationKeys.MAPS_HERE_APP_CODE).ifPresent(appCode -> params.put("appCode", appCode));
        } else if (ConfigurationKeys.GeoInfoProvider.GEOAPIFY == provider) {
            geoConf.get(ConfigurationKeys.MAPS_GEOAPIFY_API_KEY).ifPresent(key -> params.put("key", key));
        }
    }

}
