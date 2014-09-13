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
package io.bagarino.controller.form;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class EventForm {

    private final Integer id;
    private final int organizationId;
    private final String location;
    private final String description;
    private final DateTimeForm start;
    private final DateTimeForm end;
    private final BigDecimal price;
    private final String currency;
    private final int seats;
    private final BigDecimal vat;
    private final boolean vatIncluded;
    private final List<TicketCategoryForm> ticketCategories;

    @JsonCreator
    public EventForm(@JsonProperty("id") Integer id,
                     @JsonProperty("organizationId") int organizationId,
                     @JsonProperty("location") String location,
                     @JsonProperty("description") String description,
                     @JsonProperty("start") DateTimeForm start,
                     @JsonProperty("end") DateTimeForm end,
                     @JsonProperty("price") BigDecimal price,
                     @JsonProperty("currency") String currency,
                     @JsonProperty("seats") int seats,
                     @JsonProperty("vat") BigDecimal vat,
                     @JsonProperty("vatIncluded") boolean vatIncluded,
                     @JsonProperty("ticketCategories") List<TicketCategoryForm> ticketCategories) {
        this.id = id;
        this.organizationId = organizationId;
        this.location = location;
        this.description = description;
        this.start = start;
        this.end = end;
        this.price = price;
        this.currency = currency;
        this.seats = seats;
        this.vat = vat;
        this.vatIncluded = vatIncluded;
        this.ticketCategories = ticketCategories;
    }
}
