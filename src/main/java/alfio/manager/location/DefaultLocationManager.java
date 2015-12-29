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
package alfio.manager.location;

import alfio.config.Initializer;
import alfio.manager.system.ConfigurationManager;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.TimeZoneApi;
import com.google.maps.model.LatLng;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Profile(Initializer.PROFILE_LIVE)
public class DefaultLocationManager implements LocationManager {

    private static final AtomicReference<GeoApiContext> CTX = new AtomicReference<>();
    private final ConfigurationManager configurationManager;

    @Autowired
    public DefaultLocationManager(ConfigurationManager configurationManager) {
         this.configurationManager = configurationManager;
    }

    @Override
    @Cacheable
    public Pair<String, String> geocode(String address) {
        return Optional.ofNullable(GeocodingApi.geocode(getApiContext(), address).awaitIgnoreError())
                    .filter(r -> r.length > 0)
                    .map(r -> r[0].geometry.location)
                    .map(l -> Pair.of(Double.toString(l.lat), Double.toString(l.lng)))
                    .orElseThrow(() -> new LocationNotFound("No location found for address " + address));
    }

    private GeoApiContext getApiContext() {
        GeoApiContext ctx = CTX.get();
        if(ctx == null) {
            ctx = new GeoApiContext().setApiKey(configurationManager.getRequiredValue(Configuration.getSystemConfiguration(ConfigurationKeys.MAPS_SERVER_API_KEY)));
            CTX.compareAndSet(null, ctx);
        }
        return CTX.get();
    }

    @Override
    @Cacheable
    public TimeZone getTimezone(Pair<String, String> location) {
        return getTimezone(location.getLeft(), location.getRight());
    }

    @Override
    @Cacheable
    public TimeZone getTimezone(String latitude, String longitude) {
        return Optional.ofNullable(TimeZoneApi.getTimeZone(getApiContext(), new LatLng(Double.valueOf(latitude), Double.valueOf(longitude))).awaitIgnoreError())
                .orElseThrow(() -> new LocationNotFound(String.format("No TimeZone found for location having coordinates: %s,%s", latitude, longitude)));

    }
}
