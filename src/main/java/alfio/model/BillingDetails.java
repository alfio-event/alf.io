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

import alfio.model.support.JSONData;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class BillingDetails {
    private final String companyName;
    private final String addressLine1;
    private final String addressLine2;
    private final String zip;
    private final String city;
    private final String state;
    private final String country;
    private final String taxId;
    private final TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo;

    public BillingDetails(@Column("billing_address_company") String companyName,
                          @Column("billing_address_line1") String addressLine1,
                          @Column("billing_address_line2") String addressLine2,
                          @Column("billing_address_zip") String zip,
                          @Column("billing_address_city") String city,
                          @Column("billing_address_state") String state,
                          @Column("vat_country") String country,
                          @Column("vat_nr") String taxId,
                          @Column("invoicing_additional_information") @JSONData TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo) {

        this.companyName = companyName;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.zip = zip;
        this.city = city;
        this.state = state;
        this.country = country;
        this.taxId = taxId;
        this.invoicingAdditionalInfo = invoicingAdditionalInfo;
    }

    public boolean getHasTaxId() {
        return taxId != null && !taxId.isEmpty();
    }

}
