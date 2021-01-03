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
package alfio.model;

import alfio.manager.system.ConfigurationLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.time.ZoneId;

public interface EventHiddenFieldContainer {
    @JsonIgnore
    String getPrivateKey();

    @JsonIgnore
    Pair<String, String> getLatLong();

    @JsonIgnore
    ZoneId getZoneId();

    @JsonIgnore
    BigDecimal getVat();

    @JsonIgnore
    ConfigurationLevel getConfigurationLevel();
}
