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
package alfio.controller.api.v2.model;

import alfio.model.Event.EventFormat;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BasicEventInfo implements DateValidity {

    private final String shortName;
    private final String fileBlobId;
    private final Map<String, String> title;
    private final EventFormat format;
    private final String location;

    private final String timeZone;
    private final DatesWithTimeZoneOffset datesWithOffset;
    private final boolean sameDay;
    private final Map<String, String> formattedBeginDate; // day, month, year
    private final Map<String, String> formattedBeginTime; //the hour/minute component
    private final Map<String, String> formattedEndDate;
    private final Map<String, String> formattedEndTime;
    private final List<Language> contentLanguages;

    public BasicEventInfo(String shortName,
                          String fileBlobId,
                          Map<String, String> title,
                          EventFormat format,
                          String location,
                          String timeZone,
                          DatesWithTimeZoneOffset datesWithOffset,
                          boolean sameDay,
                          Map<String, String> formattedBeginDate,
                          Map<String, String> formattedBeginTime,
                          Map<String, String> formattedEndDate,
                          Map<String, String> formattedEndTime,
                          List<Language> contentLanguages) {
        this.shortName = shortName;
        this.fileBlobId = fileBlobId;
        this.title = title;
        this.format = format;
        this.location = location;
        this.timeZone = timeZone;
        this.datesWithOffset = datesWithOffset;
        this.sameDay = sameDay;
        this.formattedBeginDate = formattedBeginDate;
        this.formattedBeginTime = formattedBeginTime;
        this.formattedEndDate = formattedEndDate;
        this.formattedEndTime = formattedEndTime;
        this.contentLanguages = contentLanguages;
    }
}
