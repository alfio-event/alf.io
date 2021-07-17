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

import alfio.controller.support.FormattedEventDates;
import alfio.controller.support.Formatters;
import alfio.model.checkin.EventWithCheckInInfo;
import alfio.model.metadata.JoinLink;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.context.MessageSource;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static alfio.controller.support.Formatters.getFormattedDate;

@AllArgsConstructor
@Getter
public class OnlineCheckInInfo implements DateValidity {

    private final Map<String, String> formattedBeginDate;
    private final Map<String, String> formattedBeginTime;
    private final Map<String, String> formattedEndDate;
    private final Map<String, String> formattedEndTime;

    private final String timeZone;
    private final DatesWithTimeZoneOffset datesWithOffset;

    public boolean isSameDay() {
        return true;
    }

    /*
    getFormattedDate(event, start.withZoneSameInstant(target), "date.extended.pattern", messageSource),
            getFormattedDate(event, start.withZoneSameInstant(target), "time.extended.pattern", messageSource),
            getFormattedDate(event, end.withZoneSameInstant(target), "date.extended.pattern", messageSource),
            getFormattedDate(event, end.withZoneSameInstant(target), "time.extended.pattern", messageSource)
     */

    public static OnlineCheckInInfo fromJoinLink(JoinLink joinLink,
                                                 EventWithCheckInInfo event,
                                                 ZoneId targetTz,
                                                 MessageSource messageSource) {
        var start = joinLink.getValidFrom().atZone(event.getZoneId());
        var end = joinLink.getValidFrom().atZone(event.getZoneId());
        return fromDates(event, start, end, targetTz, messageSource);
    }

    public static OnlineCheckInInfo fromEvent(EventWithCheckInInfo event,
                                              ZoneId targetTz,
                                              MessageSource messageSource) {
        return fromDates(event, event.getBegin(), event.getEnd(), targetTz, messageSource);
    }

    private static OnlineCheckInInfo fromDates(EventWithCheckInInfo event,
                                               ZonedDateTime start,
                                               ZonedDateTime end,
                                               ZoneId targetTz,
                                               MessageSource messageSource) {
        return new OnlineCheckInInfo(
            getFormattedDate(event, start.withZoneSameInstant(targetTz), "date.extended.pattern", messageSource),
            getFormattedDate(event, start.withZoneSameInstant(targetTz), "time.extended.pattern", messageSource),
            getFormattedDate(event, end.withZoneSameInstant(targetTz), "date.extended.pattern", messageSource),
            getFormattedDate(event, end.withZoneSameInstant(targetTz), "time.extended.pattern", messageSource),
            event.getZoneId().toString(),
            DatesWithTimeZoneOffset.fromDates(start, end)
        );
    }
}
