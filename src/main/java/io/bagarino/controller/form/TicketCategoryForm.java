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

@Getter
public class TicketCategoryForm {

    private final Integer id;
    private final String name;
    private final BigDecimal seats;
    private final DateTimeForm inception;
    private final DateTimeForm expiration;
    private final BigDecimal discount;

    @JsonCreator
    public TicketCategoryForm(@JsonProperty("id") Integer id,
                              @JsonProperty("name") String name,
                              @JsonProperty("seats") BigDecimal seats,
                              @JsonProperty("inception") DateTimeForm inception,
                              @JsonProperty("expiration") DateTimeForm expiration,
                              @JsonProperty("discount") BigDecimal discount) {
        this.id = id;
        this.name = name;
        this.seats = seats;
        this.inception = inception;
        this.expiration = expiration;
        this.discount = discount;
    }
}
