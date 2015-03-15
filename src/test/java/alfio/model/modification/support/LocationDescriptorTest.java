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

import com.insightfullogic.lambdabehave.JunitSuiteRunner;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.TimeZone;

import static com.insightfullogic.lambdabehave.Suite.describe;

@RunWith(JunitSuiteRunner.class)
public class LocationDescriptorTest {{

    final String latitude = "latitude";
    final String longitude = "longitude";
    final TimeZone timeZone = TimeZone.getDefault();
    final LocationDescriptor expected = new LocationDescriptor(timeZone.getID(), latitude, longitude, "https://maps.googleapis.com/maps/api/staticmap?center=latitude,longitude&key=mapKey&zoom=16&size=400x400&markers=color:blue%7Clabel:E%7Clatitude,longitude");

    describe("LocationDescriptor", it -> {
        it.should("build the locationDescriptor", expect -> expect.that(LocationDescriptor.fromGeoData(Pair.of(latitude, longitude), timeZone, Optional.of("mapKey"))).isEqualTo(expected));
    });
}}