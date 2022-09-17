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

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
public class AdditionalServiceItemExport {
    private final String uuid;
    private final ZonedDateTime utcCreation;
    private final ZonedDateTime utcLastModified;
    private final String ticketsReservationUuid;
    private final String firstName;
    private final String lastName;
    private final String emailAddress;
    private final String additionalServiceTitle;
    private final AdditionalService.AdditionalServiceType additionalServiceType;

    private final AdditionalServiceItem.AdditionalServiceItemStatus additionalServiceItemStatus;

    private final Integer finalPriceCts;
    private final String currencyCode;
    private final Integer vatCts;
    private final Integer discountCts;

    public AdditionalServiceItemExport(@Column("ai_uuid") String uuid,
                                       @Column("ai_creation") ZonedDateTime utcCreation,
                                       @Column("ai_last_modified") ZonedDateTime utcLastModified,
                                       @Column("tr_uuid") String ticketsReservationUuid,
                                       @Column("tr_first_name") String firstName,
                                       @Column("tr_last_name") String lastName,
                                       @Column("tr_email_address") String emailAddress,
                                       @Column("as_title") String additionalServiceTitle,
                                       @Column("as_type") AdditionalService.AdditionalServiceType additionalServiceType,
                                       @Column("ai_final_price_cts") Integer finalPriceCts,
                                       @Column("ai_currency_code") String currencyCode,
                                       @Column("ai_vat_cts") Integer vatCts,
                                       @Column("ai_discount_cts") Integer discountCts,
                                       @Column("ai_status") AdditionalServiceItem.AdditionalServiceItemStatus additionalServiceItemStatus) {
        this.uuid = uuid;
        this.utcCreation = utcCreation;
        this.utcLastModified = utcLastModified;
        this.ticketsReservationUuid = ticketsReservationUuid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.additionalServiceTitle = additionalServiceTitle;
        this.additionalServiceType = additionalServiceType;
        this.finalPriceCts = finalPriceCts;
        this.currencyCode = currencyCode;
        this.vatCts = vatCts;
        this.discountCts = discountCts;
        this.additionalServiceItemStatus = additionalServiceItemStatus;
    }
}
