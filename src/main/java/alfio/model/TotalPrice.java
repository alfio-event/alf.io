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
package alfio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class TotalPrice {
    private final int priceWithVAT;
    private final int VAT;
    private final int discount;
    private final int discountAppliedCount;

    @JsonCreator
    public TotalPrice(@JsonProperty("priceWithVAT") int priceWithVAT,
                      @JsonProperty("vat") int vat,
                      @JsonProperty("discount") int discount,
                      @JsonProperty("discountAppliedCount") int discountAppliedCount) {
        this.priceWithVAT = priceWithVAT;
        this.VAT = vat;
        this.discount = discount;
        this.discountAppliedCount = discountAppliedCount;
    }
}
