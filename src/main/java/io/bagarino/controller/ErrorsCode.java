/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.controller;

public interface ErrorsCode {

	String STEP_1_SELECT_AT_LEAST_ONE = "error.STEP_1_SELECT_AT_LEAST_ONE";
	String STEP_1_OVER_MAXIMUM = "error.STEP_1_OVER_MAXIMUM";
	String STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE = "error.STEP_1_TICKET_CATEGORY_MUST_BE_SALEABLE";
	String STEP_1_ACCESS_RESTRICTED = "error.STEP_1_ACCESS_RESTRICTED";
	String STEP_1_NOT_ENOUGH_TICKETS = "error.STEP_1_NOT_ENOUGH_TICKETS";
}
