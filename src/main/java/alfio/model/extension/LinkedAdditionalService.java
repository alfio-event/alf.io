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

import java.util.Map;

public class LinkedAdditionalService {
    private final int additionalServiceId;
    private final int quantity;
    private final Map<String, String> additional;

    public LinkedAdditionalService(int additionalServiceId, int quantity, Map<String, String> additional) {
        this.additionalServiceId = additionalServiceId;
        this.quantity = quantity;
        this.additional = Map.copyOf(additional);
    }

    public int getAdditionalServiceId() {
        return additionalServiceId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Map<String, String> getAdditional() {
        return additional;
    }
}
