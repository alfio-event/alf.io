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

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.cache.annotation.Cacheable;

import java.util.TimeZone;

public interface LocationManager {
    @Cacheable
    Pair<String, String> geocode(String address);

    @Cacheable
    TimeZone getTimezone(Pair<String, String> location);

    @Cacheable
    TimeZone getTimezone(String latitude, String longitude);
}
