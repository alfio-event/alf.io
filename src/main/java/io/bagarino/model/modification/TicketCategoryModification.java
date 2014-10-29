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
package io.bagarino.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.bagarino.model.TicketCategory;
import io.bagarino.util.MonetaryUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Optional;

@Getter
public class TicketCategoryModification {

    private final Integer id;
    private final String name;
    private final int maxTickets;
    private final DateTimeModification inception;
    private final DateTimeModification expiration;
    private final String description;
    private final BigDecimal price;
    private final boolean tokenGenerationRequested;

    @JsonCreator
    public TicketCategoryModification(@JsonProperty("id") Integer id,
                                      @JsonProperty("name") String name,
                                      @JsonProperty("maxTickets") int maxTickets,
                                      @JsonProperty("inception") DateTimeModification inception,
                                      @JsonProperty("expiration") DateTimeModification expiration,
                                      @JsonProperty("description") String description,
                                      @JsonProperty("price") BigDecimal price,
                                      @JsonProperty("tokenGenerationRequested") boolean tokenGenerationRequested) {
        this.id = id;
        this.name = name;
        this.maxTickets = maxTickets;
        this.inception = inception;
        this.expiration = expiration;
        this.description = description;
        this.price = price;
        this.tokenGenerationRequested = tokenGenerationRequested;
    }

    public int getPriceInCents() {
        return Optional.ofNullable(price).map(MonetaryUtil::unitToCents).orElse(0);
    }

    public static TicketCategoryModification fromTicketCategory(TicketCategory tc, ZoneId zoneId) {
        return new TicketCategoryModification(tc.getId(),
                tc.getName(),
                tc.getMaxTickets(),
                DateTimeModification.fromZonedDateTime(tc.getInception(zoneId)),
                DateTimeModification.fromZonedDateTime(tc.getExpiration(zoneId)),
                tc.getDescription(),
                tc.getPrice(),
                tc.isAccessRestricted());
    }
}
