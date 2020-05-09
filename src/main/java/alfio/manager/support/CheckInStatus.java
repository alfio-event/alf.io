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
package alfio.manager.support;

public enum CheckInStatus {
    EVENT_NOT_FOUND,
    TICKET_NOT_FOUND,
    EMPTY_TICKET_CODE,
    INVALID_TICKET_CODE,
    INVALID_TICKET_STATE,
    ALREADY_CHECK_IN,
    MUST_PAY,
    OK_READY_TO_BE_CHECKED_IN,
    SUCCESS,
    INVALID_TICKET_CATEGORY_CHECK_IN_DATE,
    BADGE_SCAN_ALREADY_DONE,
    OK_READY_FOR_BADGE_SCAN,
    BADGE_SCAN_SUCCESS,
    ERROR
}
