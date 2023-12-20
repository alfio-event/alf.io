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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.ZonedDateTime;

public interface EventCheckInInfo extends TimeZoneInfo {

    String VERSION_FOR_CODE_CASE_INSENSITIVE = "205.2.0.0.50";
    String VERSION_FOR_LINKED_ADDITIONAL_SERVICE = "205.2.0.0.52";

    int getId();
    String getPrivateKey();
    ZonedDateTime getBegin();
    ZonedDateTime getEnd();
    Event.EventFormat getFormat();

    @JsonIgnore
    boolean supportsQRCodeCaseInsensitive();

    @JsonIgnore
    boolean supportsLinkedAdditionalServices();

}
