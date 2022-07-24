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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.ConfigurationKeys;
import com.moodysalem.TimezoneMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.*;

@RestController
@RequestMapping("/admin/api")
public class LocationApiController {

    private static final Logger log = LoggerFactory.getLogger(LocationApiController.class);

    private final ConfigurationManager configurationManager;

    @Autowired
    public LocationApiController(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
       log.error("Exception in location api", e);
        return e.getMessage();
    }

    @GetMapping("/location/timezones")
    public List<String> getTimezones() {
        List<String> s = new ArrayList<>(ZoneId.getAvailableZoneIds());
        s.sort(String::compareTo);
        return s;
    }

    @GetMapping("/location/timezone")
    public String getTimezone(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        String tzId = TimezoneMapper.tzNameAt(lat, lng);
        return getTimezones().contains(tzId) ? tzId : null;
    }



    @GetMapping("/location/static-map-image")
    public String getMapImage(
        @RequestParam(name = "lat", required = false) String lat,
        @RequestParam(name = "lng", required = false) String lng) {
        return LocationDescriptor.getMapUrl(lat, lng, getGeoConf());
    }

    private Map<ConfigurationKeys, ConfigurationManager.MaybeConfiguration> getGeoConf() {
        var keys = Set.of(MAPS_PROVIDER, MAPS_CLIENT_API_KEY, MAPS_HERE_API_KEY);
        return configurationManager.getFor(keys, ConfigurationLevel.system());
    }

    @GetMapping("/location/map-provider-client-api-key")
    public ProviderAndKeys getGeoInfoProviderAndKeys() {
        var geoInfoConfiguration = getGeoConf();
        ConfigurationKeys.GeoInfoProvider provider = LocationDescriptor.getProvider(geoInfoConfiguration);
        Map<ConfigurationKeys, String> apiKeys = new EnumMap<>(ConfigurationKeys.class);
        geoInfoConfiguration.forEach((k,v) -> v.getValue().ifPresent(value -> apiKeys.put(k, value)));
        return new ProviderAndKeys(provider, apiKeys);
    }

    public record ProviderAndKeys(GeoInfoProvider provider, Map<ConfigurationKeys, String> keys) {
    }
}
