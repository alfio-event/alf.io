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
package alfio.controller.api.admin;

import alfio.manager.system.ConfigurationManager;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import com.moodysalem.TimezoneMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/admin/api")
public class LocationApiController {

    private final ConfigurationManager configurationManager;

    @Autowired
    public LocationApiController(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        return e.getMessage();
    }

    @RequestMapping("/location/timezones")
    public List<String> getTimezones() {
        List<String> s = new ArrayList<>(ZoneId.getAvailableZoneIds());
        s.sort(String::compareTo);
        return s;
    }

    @RequestMapping("/location/timezone")
    public String getTimezone(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        String tzId = TimezoneMapper.tzNameAt(lat, lng);
        return getTimezones().contains(tzId) ? tzId : null;
    }



    @RequestMapping("/location/static-map-image")
    public String getMapImage(
        @RequestParam(name = "lat", required = false) String lat,
        @RequestParam(name = "lng", required = false) String lng) {
        Map<ConfigurationKeys, Optional<String>> geoInfoConfiguration = getGeoConf();
        return LocationDescriptor.getMapUrl(lat, lng, geoInfoConfiguration);
    }

    private Map<ConfigurationKeys, Optional<String>> getGeoConf() {
        Function<ConfigurationKeys, Configuration.ConfigurationPathKey> pathKeyBuilder = Configuration::getSystemConfiguration;
        return configurationManager.getStringConfigValueFrom(
                pathKeyBuilder.apply(ConfigurationKeys.MAPS_PROVIDER),
                pathKeyBuilder.apply(ConfigurationKeys.MAPS_CLIENT_API_KEY),
                pathKeyBuilder.apply(ConfigurationKeys.MAPS_HERE_APP_ID),
                pathKeyBuilder.apply(ConfigurationKeys.MAPS_HERE_APP_CODE));
    }

    @RequestMapping("/location/map-provider-client-api-key")
    public ProviderAndKeys getGeoInfoProviderAndKeys() {
        Map<ConfigurationKeys, Optional<String>> geoInfoConfiguration = getGeoConf();
        ConfigurationKeys.GeoInfoProvider provider = LocationDescriptor.getProvider(geoInfoConfiguration);
        Map<ConfigurationKeys, String> apiKeys = new HashMap<>();
        geoInfoConfiguration.forEach((k,v) -> v.ifPresent(value -> apiKeys.put(k, value)));
        return new ProviderAndKeys(provider, apiKeys);
    }

    @AllArgsConstructor
    @Getter
    public static class ProviderAndKeys {
        private final ConfigurationKeys.GeoInfoProvider provider;
        private Map<ConfigurationKeys, String> keys;
    }
}
