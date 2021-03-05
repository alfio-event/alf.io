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
package alfio.manager.payment;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@UtilityClass
class MetadataBuilder {


    static final String RESERVATION_ID = "reservationId";

    static Map<String, String> buildMetadata(PaymentSpecification spec, Map<String, String> base) {
        Map<String, String> initialMetadata = new HashMap<>(base);
        initialMetadata.put(RESERVATION_ID, spec.getReservationId());
        initialMetadata.put("email", spec.getEmail());
        initialMetadata.put("fullName", spec.getCustomerName().getFullName());
        if (StringUtils.isNotBlank(spec.getBillingAddress())) {
            initialMetadata.put("billingAddress", spec.getBillingAddress().lines().collect(Collectors.joining(",")));
        }
        return initialMetadata;
    }

}
