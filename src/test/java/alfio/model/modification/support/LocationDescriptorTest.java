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
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class LocationDescriptorTest {

    private final String latitude = "latitude";
    private final String longitude = "longitude";
    private final TimeZone timeZone = TimeZone.getDefault();
    private Function<String, LocationDescriptor> locationDescriptorBuilder = (mapUrl) -> new LocationDescriptor(timeZone.getID(), latitude, longitude, mapUrl);

    @Test
    public void testLocationDescriptorGoogle() {
        Map<ConfigurationKeys, Optional<String>> geoInfo = Collections.singletonMap(ConfigurationKeys.MAPS_CLIENT_API_KEY, Optional.of("mapKey"));
        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://maps.googleapis.com/maps/api/staticmap?center=latitude,longitude&key=mapKey&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7Clatitude,longitude");
        assertEquals(expected, LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorNone() {
        Map<ConfigurationKeys, Optional<String>> geoInfo = Collections.emptyMap();
        final LocationDescriptor expected = locationDescriptorBuilder.apply("");
        assertEquals(expected, LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorGoogleWithTypeSet() {
        Map<ConfigurationKeys, Optional<String>> geoInfo = new HashMap<>();

        geoInfo.put(ConfigurationKeys.MAPS_PROVIDER, Optional.of(ConfigurationKeys.GeoInfoProvider.GOOGLE.name()));
        geoInfo.put(ConfigurationKeys.MAPS_CLIENT_API_KEY, Optional.of("mapKey"));

        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://maps.googleapis.com/maps/api/staticmap?center=latitude,longitude&key=mapKey&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7Clatitude,longitude");
        assertEquals(expected, LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorHEREWithTypeSet() {
        Map<ConfigurationKeys, Optional<String>> geoInfo = new HashMap<>();
        geoInfo.put(ConfigurationKeys.MAPS_PROVIDER, Optional.of(ConfigurationKeys.GeoInfoProvider.HERE.name()));
        geoInfo.put(ConfigurationKeys.MAPS_HERE_APP_ID, Optional.of("appId"));
        geoInfo.put(ConfigurationKeys.MAPS_HERE_APP_CODE, Optional.of("appCode"));

        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://image.maps.api.here.com/mia/1.6/mapview?c=latitude,longitude&z=16&w=400&h=400&poi=latitude,longitude&app_id=appId&app_code=appCode");
        assertEquals(expected, LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorNONEWithTypeSet() {
        Map<ConfigurationKeys, Optional<String>> geoInfo = new HashMap<>();
        geoInfo.put(ConfigurationKeys.MAPS_PROVIDER, Optional.of(ConfigurationKeys.GeoInfoProvider.NONE.name()));
        final LocationDescriptor expected = locationDescriptorBuilder.apply("");
        assertEquals(expected, LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, geoInfo));
    }

}