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
import alfio.model.system.ConfigurationKeyValuePathLevel;
import alfio.model.system.ConfigurationKeys;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LocationDescriptorTest {

    private final String latitude = "latitude";
    private final String longitude = "longitude";
    private final TimeZone timeZone = TimeZone.getDefault();
    private Function<String, LocationDescriptor> locationDescriptorBuilder = (mapUrl) -> new LocationDescriptor(timeZone.getID(), latitude, longitude, mapUrl);


    private static ConfigurationManager.MaybeConfiguration buildMaybeConf(ConfigurationKeys k, String val) {
        return new ConfigurationManager.MaybeConfiguration(k, new ConfigurationKeyValuePathLevel(k.name(), val, null));
    }

    @Test
    public void testLocationDescriptorGoogle() {
        var geoInfo = Map.of(ConfigurationKeys.MAPS_CLIENT_API_KEY, buildMaybeConf(ConfigurationKeys.MAPS_CLIENT_API_KEY, "mapKey"));
        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://maps.googleapis.com/maps/api/staticmap?center=latitude,longitude&key=mapKey&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7Clatitude,longitude");
        assertEquals(expected, LocationDescriptor.fromGeoData(EventFormat.IN_PERSON, Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorNone() {
        Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> geoInfo = Collections.emptyMap();
        final LocationDescriptor expected = locationDescriptorBuilder.apply("");
        assertEquals(expected, LocationDescriptor.fromGeoData(EventFormat.IN_PERSON, Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorGoogleWithTypeSet() {
        var geoInfo = Map.of(
            ConfigurationKeys.MAPS_PROVIDER, buildMaybeConf(ConfigurationKeys.MAPS_PROVIDER, ConfigurationKeys.GeoInfoProvider.GOOGLE.name()),
            ConfigurationKeys.MAPS_CLIENT_API_KEY, buildMaybeConf(ConfigurationKeys.MAPS_CLIENT_API_KEY, "mapKey"));

        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://maps.googleapis.com/maps/api/staticmap?center=latitude,longitude&key=mapKey&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7Clatitude,longitude");
        assertEquals(expected, LocationDescriptor.fromGeoData(EventFormat.IN_PERSON, Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorHEREWithTypeSet() {
        var geoInfo = Map.of(
            ConfigurationKeys.MAPS_PROVIDER, buildMaybeConf(ConfigurationKeys.MAPS_PROVIDER, ConfigurationKeys.GeoInfoProvider.HERE.name()),
            ConfigurationKeys.MAPS_HERE_API_KEY, buildMaybeConf(ConfigurationKeys.MAPS_HERE_API_KEY, "apiKey")
        );

        final LocationDescriptor expected = locationDescriptorBuilder.apply("https://image.maps.ls.hereapi.com/mia/1.6/mapview?c=latitude,longitude&z=16&w=400&h=400&poi=latitude,longitude&apikey=apiKey");
        assertEquals(expected, LocationDescriptor.fromGeoData(EventFormat.IN_PERSON, Pair.of(latitude, longitude), timeZone, geoInfo));
    }

    @Test
    public void testLocationDescriptorNONEWithTypeSet() {
        var geoInfo = Map.of(ConfigurationKeys.MAPS_PROVIDER, buildMaybeConf(ConfigurationKeys.MAPS_PROVIDER, ConfigurationKeys.GeoInfoProvider.NONE.name()));
        final LocationDescriptor expected = locationDescriptorBuilder.apply("");
        assertEquals(expected, LocationDescriptor.fromGeoData(EventFormat.IN_PERSON, Pair.of(latitude, longitude), timeZone, geoInfo));
    }

}