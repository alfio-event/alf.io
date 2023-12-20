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

import alfio.controller.api.support.AdditionalServiceWithData;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class TicketInfo implements DateValidity {

    private final String fullName;
    private final String email;
    private final String uuid;

    private final String ticketCategoryName;
    private final String reservationFullName;
    private final String reservationId;

    private final boolean deskPaymentRequired;

    //date related
    private final String timeZone;
    private final DatesWithTimeZoneOffset datesWithOffset;
    private final boolean sameDay;
    private final Map<String, String> formattedBeginDate; // day, month, year
    private final Map<String, String> formattedBeginTime; //the hour/minute component
    private final Map<String, String> formattedEndDate;
    private final Map<String, String> formattedEndTime;
    //
    private final List<AdditionalServiceWithData> additionalServiceWithData;
}
