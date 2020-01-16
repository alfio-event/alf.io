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
package alfio.model.transaction;

import alfio.model.BillingDetails;
import alfio.model.TotalPrice;
import lombok.Data;

@Data
public class TransactionRequest {

    private static final TransactionRequest EMPTY = new TransactionRequest(null, null);

    private final TotalPrice price;
    private final BillingDetails billingDetails;

    public static TransactionRequest empty() {
        return EMPTY;
    }
}
