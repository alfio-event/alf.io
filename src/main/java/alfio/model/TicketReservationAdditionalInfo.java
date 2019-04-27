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

import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.util.Optional;

@Getter
public class TicketReservationAdditionalInfo {

    private final String billingAddressCompany;
    private final String billingAddressLine1;
    private final String billingAddressLine2;
    private final String billingAddressZip;
    private final String billingAddressCity;
    private final Boolean validated;
    private final Boolean skipVatNr;
    private final Boolean addCompanyBillingDetails;
    private final TicketReservationInvoicingAdditionalInfo invoicingAdditionalInfo;

    public TicketReservationAdditionalInfo(@Column("billing_address_company") String billingAddressCompany,
                                           @Column("billing_address_line1") String billingAddressLine1,
                                           @Column("billing_address_line2") String billingAddressLine2,
                                           @Column("billing_address_zip") String billingAddressZip,
                                           @Column("billing_address_city") String billingAddressCity,
                                           @Column("validated_for_overview") Boolean validated,
                                           @Column("skip_vat_nr") Boolean skipVatNr,
                                           @Column("add_company_billing_details") Boolean addCompanyBillingDetails,
                                           @Column("invoicing_additional_information") String invoicingAdditionalInformation) {
        this.billingAddressCompany = billingAddressCompany;
        this.billingAddressLine1 = billingAddressLine1;
        this.billingAddressLine2 = billingAddressLine2;
        this.billingAddressZip = billingAddressZip;
        this.billingAddressCity = billingAddressCity;
        this.addCompanyBillingDetails = addCompanyBillingDetails;
        this.validated = validated;
        this.skipVatNr = skipVatNr;
        this.invoicingAdditionalInfo = invoicingAdditionalInformation == null ? new TicketReservationInvoicingAdditionalInfo(null) :
            Json.fromJson(invoicingAdditionalInformation, TicketReservationInvoicingAdditionalInfo.class);
    }


    public boolean hasBeenValidated() {
        return Optional.ofNullable(validated).orElse(false);
    }

    public boolean hasSkipVatNr() {
        return Optional.ofNullable(skipVatNr).orElse(false);
    }
}