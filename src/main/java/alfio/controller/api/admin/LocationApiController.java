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

import alfio.manager.location.LocationManager;
import alfio.manager.location.LocationNotFound;
import alfio.manager.system.ConfigurationManager;
import alfio.model.modification.support.LocationDescriptor;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.TimeZone;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

@RestController
@RequestMapping("/admin/api")
public class LocationApiController {

    private final LocationManager locationManager;
    private final ConfigurationManager configurationManager;

    @Autowired
    public LocationApiController(LocationManager locationManager, ConfigurationManager configurationManager) {
        this.locationManager = locationManager;
        this.configurationManager = configurationManager;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unhandledException(Exception e) {
        return e.getMessage();
    }

    @ExceptionHandler(LocationNotFound.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String locationException(Exception e) {
        return e.getMessage();
    }

    @RequestMapping(value = "/location/geo", method = GET)
    public LocationDescriptor geocodeAddress(@RequestParam("location") String address) {
        Pair<String, String> coordinates = locationManager.geocode(address);
        TimeZone timezone = locationManager.getTimezone(coordinates);
        return LocationDescriptor.fromGeoData(coordinates, timezone, getMapsClientApiKey());
    }

    private Optional<String> getMapsClientApiKey() {
        return configurationManager.getStringConfigValue(Configuration.getSystemConfiguration(ConfigurationKeys.MAPS_CLIENT_API_KEY));
    }

    @RequestMapping(value = "/location/map", method = GET)
    public String getMapUrl(@RequestParam("lat") String latitude, @RequestParam("long") String longitude) {
        Validate.notBlank(latitude);
        Validate.notBlank(longitude);
        LocationDescriptor descriptor = LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), TimeZone.getDefault(), getMapsClientApiKey());
        return descriptor.getMapUrl();
    }
}
