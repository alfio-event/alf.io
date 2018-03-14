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

import alfio.model.AdditionalService;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class AdditionalServiceReservationModification implements Serializable {
    private Integer additionalServiceId;
    private BigDecimal amount;
    private Integer quantity = 1;

    public boolean isQuantityValid(AdditionalService as, int selectionCount) {
        if(quantity != null && as.getSupplementPolicy() != null) {
            return as.getSupplementPolicy().isValid(quantity, as, selectionCount);
        } else {
            return true;
        }
    }
}
