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

import alfio.model.PromoCodeDiscount;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.metadata.AlfioMetadata;
import alfio.util.MonetaryUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public class PromotionalEmailModification {

    private final List<String> recipients;
    private final String subject;
    private final String message;

    @JsonCreator
    public PromotionalEmailModification(
        @JsonProperty("recipients") List<String> recipients,
        @JsonProperty("subject") String subject,
        @JsonProperty("message") String message) {

        this.recipients = Optional.ofNullable(recipients).map(l -> l.stream().filter(Objects::nonNull).collect(Collectors.toList())).orElse(Collections.emptyList());
        this.subject = subject;
        this.message = message;
    }
}
