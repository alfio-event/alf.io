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
package io.bagarino.model;

import io.bagarino.model.user.Organization;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collection;

@Data
public class Event {
    private final String description;
    private final Collection<TicketCategory> ticketCategories;
    private final Organization owner;
    private final String latitude;
    private final String longitude;
    private final LocalDateTime begin;
    private final LocalDateTime end;
}
