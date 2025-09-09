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
package alfio.controller.support;

import java.beans.ConstructorProperties;
import java.util.Map;

public class FormattedEventDates {
    public final Map<String, String> beginDate;
    public final Map<String, String> beginTime;
    public final Map<String, String> endDate;
    public final Map<String, String> endTime;

    @ConstructorProperties({"beginDate", "beginTime", "endDate", "endTime"})
    public FormattedEventDates(Map<String, String> beginDate, Map<String, String> beginTime, Map<String, String> endDate, Map<String, String> endTime) {
        this.beginDate = beginDate;
        this.beginTime = beginTime;
        this.endDate = endDate;
        this.endTime = endTime;
    }
}
