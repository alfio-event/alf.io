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
package alfio.model.support;

import alfio.model.PriceContainer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TicketInfo {
    private final String id;
    private final String firstName;
    private final String lastName;
    private final String type;
    private final String status;
    private final Integer finalPriceCts;
    private final Integer srcPriceCts;
    private final Integer taxCts;
    private final PriceContainer.VatStatus taxStatus;

    @JsonCreator
    private TicketInfo(@JsonProperty("id") String id,
                       @JsonProperty("firstName") String firstName,
                       @JsonProperty("lastName") String lastName,
                       @JsonProperty("type") String type,
                       @JsonProperty("status") String status,
                       @JsonProperty("finalPriceCts") Integer finalPriceCts,
                       @JsonProperty("srcPriceCts") Integer srcPriceCts,
                       @JsonProperty("taxCts") Integer taxCts,
                       @JsonProperty("taxStatus") PriceContainer.VatStatus taxStatus) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.type = type;
        this.status = status;
        this.finalPriceCts = finalPriceCts;
        this.srcPriceCts = srcPriceCts;
        this.taxCts = taxCts;
        this.taxStatus = taxStatus;
    }

    public String getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public Integer getFinalPriceCts() {
        return finalPriceCts;
    }

    public Integer getSrcPriceCts() {
        return srcPriceCts;
    }

    public Integer getTaxCts() {
        return taxCts;
    }

    public PriceContainer.VatStatus getTaxStatus() {
        return taxStatus;
    }
}
