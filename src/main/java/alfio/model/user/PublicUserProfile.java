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
package alfio.model.user;

import alfio.model.BillingDetails;
import alfio.model.TicketReservationInvoicingAdditionalInfo;
import alfio.model.support.JSONData;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

@Getter
public class PublicUserProfile {
    private final BillingDetails billingDetails;
    private final Map<String, AdditionalInfoWithLabel> additionalData;

    public PublicUserProfile(@Column("billing_address_company") String companyName,
                             @Column("billing_address_line1") String addressLine1,
                             @Column("billing_address_line2") String addressLine2,
                             @Column("billing_address_zip") String zip,
                             @Column("billing_address_city") String city,
                             @Column("billing_address_state") String state,
                             @Column("vat_country") String country,
                             @Column("vat_nr") String taxId,
                             @Column("invoicing_additional_information") @JSONData TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo,
                             @Column("additional_fields") String additionalData) {
        this.billingDetails = new BillingDetails(companyName, addressLine1, addressLine2, zip, city, state, country, taxId, invoicingAdditionalInfo);
        this.additionalData = Json.fromJson(additionalData, new TypeReference<>() { });
    }
}
