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
package alfio.model.modification;

import alfio.model.TicketCategory;
import alfio.model.TicketCategory.TicketCheckInStrategy;
import alfio.model.metadata.AlfioMetadata;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
public class TicketCategoryModification {

    private final Integer id;
    private final String name;
    private final TicketCategory.TicketAccessType ticketAccessType;
    private final int maxTickets;
    private final DateTimeModification inception;
    private final DateTimeModification expiration;
    private final Map<String, String> description;
    private final BigDecimal price;
    private final boolean tokenGenerationRequested;
    private final String dateString;
    private final boolean bounded;
    private final String code;

    private final DateTimeModification validCheckInFrom;
    private final DateTimeModification validCheckInTo;

    private final DateTimeModification ticketValidityStart;
    private final DateTimeModification ticketValidityEnd;
    private final int ordinal;
    private final TicketCheckInStrategy ticketCheckInStrategy;
    private final String badgeColor;
    private final AlfioMetadata metadata;
    private final boolean skipWaitingList;

    @JsonCreator
    public TicketCategoryModification(@JsonProperty("id") Integer id,
                                      @JsonProperty("name") String name,
                                      @JsonProperty("ticketAccessType") TicketCategory.TicketAccessType ticketAccessType,
                                      @JsonProperty("maxTickets") int maxTickets,
                                      @JsonProperty("inception") DateTimeModification inception,
                                      @JsonProperty("expiration") DateTimeModification expiration,
                                      @JsonProperty("description") Map<String, String> description,
                                      @JsonProperty("price") BigDecimal price,
                                      @JsonProperty("tokenGenerationRequested") boolean tokenGenerationRequested,
                                      @JsonProperty("dateString") String dateString,
                                      @JsonProperty("bounded") boolean bounded,
                                      @JsonProperty("code") String code,
                                      @JsonProperty("validCheckInFrom") DateTimeModification validCheckInFrom,
                                      @JsonProperty("validCheckInTo") DateTimeModification validCheckInTo,
                                      @JsonProperty("ticketValidityStart") DateTimeModification ticketValidityStart,
                                      @JsonProperty("ticketValidityEnd") DateTimeModification ticketValidityEnd,
                                      @JsonProperty("ordinal") Integer ordinal,
                                      @JsonProperty("ticketCheckInStrategy") TicketCheckInStrategy ticketCheckInStrategy,
                                      @JsonProperty("badgeColor") String badgeColor,
                                      @JsonProperty("metadata") AlfioMetadata metadata) {
        this(id,
            name,
            ticketAccessType,
            maxTickets,
            inception,
            expiration,
            description,
            price,
            tokenGenerationRequested,
            dateString,
            bounded,
            code,
            validCheckInFrom,
            validCheckInTo,
            ticketValidityStart,
            ticketValidityEnd,
            ordinal,
            ticketCheckInStrategy,
            badgeColor,
            metadata,
            false);
    }

    public TicketCategoryModification(Integer id,
                                      String name,
                                      TicketCategory.TicketAccessType ticketAccessType,
                                      int maxTickets,
                                      DateTimeModification inception,
                                      DateTimeModification expiration,
                                      Map<String, String> description,
                                      BigDecimal price,
                                      boolean tokenGenerationRequested,
                                      String dateString,
                                      boolean bounded,
                                      String code,
                                      DateTimeModification validCheckInFrom,
                                      DateTimeModification validCheckInTo,
                                      DateTimeModification ticketValidityStart,
                                      DateTimeModification ticketValidityEnd,
                                      Integer ordinal,
                                      TicketCheckInStrategy ticketCheckInStrategy,
                                      String badgeColor,
                                      AlfioMetadata metadata,
                                      Boolean skipWaitingList) {
        this.id = id;
        this.name = name;
        this.ticketAccessType = ticketAccessType;
        this.maxTickets = maxTickets;
        this.inception = inception;
        this.expiration = expiration;
        this.description = description;
        this.price = price;
        this.tokenGenerationRequested = tokenGenerationRequested;
        this.dateString = dateString;
        this.bounded = bounded;
        this.code = code;
        this.validCheckInFrom = validCheckInFrom;
        this.validCheckInTo = validCheckInTo;
        this.ticketValidityStart = ticketValidityStart;
        this.ticketValidityEnd = ticketValidityEnd;
        this.ordinal = ordinal != null ? ordinal : 0;
        this.ticketCheckInStrategy = ticketCheckInStrategy;
        this.badgeColor = badgeColor;
        this.metadata = metadata;
        this.skipWaitingList = Boolean.TRUE.equals(skipWaitingList);
    }

}
