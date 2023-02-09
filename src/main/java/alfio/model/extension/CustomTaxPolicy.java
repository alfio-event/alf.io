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
package alfio.model.extension;

import alfio.model.PriceContainer;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class CustomTaxPolicy {

    private final Set<TicketTaxPolicy> ticketPolicies;

    @JsonCreator
    public CustomTaxPolicy(@JsonProperty("ticketPolicies") List<TicketTaxPolicy> ticketPolicies) {
        this.ticketPolicies = new HashSet<>(ticketPolicies);
    }

    public Set<TicketTaxPolicy> getTicketPolicies() {
        return ticketPolicies;
    }

    public static class TicketTaxPolicy implements Comparable<TicketTaxPolicy> {
        private final String uuid;
        private final PriceContainer.VatStatus taxPolicy;

        @JsonCreator
        public TicketTaxPolicy(@JsonProperty("uuid") String uuid,
                               @JsonProperty("taxPolicy") PriceContainer.VatStatus taxPolicy) {
            this.uuid = Objects.requireNonNull(uuid);
            this.taxPolicy = taxPolicy;
        }

        public PriceContainer.VatStatus getTaxPolicy() {
            return taxPolicy;
        }

        public String getUuid() {
            return uuid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TicketTaxPolicy)) return false;
            TicketTaxPolicy that = (TicketTaxPolicy) o;
            return Objects.equals(this.uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
        }

        @Override
        public int compareTo(TicketTaxPolicy o) {
            return this.equals(o) ? 0 : this.uuid.compareTo(o.uuid);
        }
    }
}
