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

import alfio.model.support.Array;
import alfio.util.MonetaryUtil;
import alfio.util.checkin.NameNormalizer;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.util.*;

@Getter
public class Ticket implements TicketInfoContainer {

    public enum TicketStatus {
        FREE, PENDING, TO_BE_PAID, ACQUIRED, CANCELLED, CHECKED_IN, EXPIRED, INVALIDATED, RELEASED, PRE_RESERVED
    }

    private static final Set<TicketStatus> SOLD_STATUSES = EnumSet.of(TicketStatus.TO_BE_PAID, TicketStatus.ACQUIRED, TicketStatus.CANCELLED, TicketStatus.CHECKED_IN, TicketStatus.RELEASED);

    private final int id;
    private final String uuid;
    private final UUID publicUuid;
    private final ZonedDateTime creation;
    private final Integer categoryId;
    private final int eventId;
    private final TicketStatus status;
    private final String ticketsReservationId;
    private final String fullName;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final boolean lockedAssignment;
    private final String userLanguage;

    private final int srcPriceCts;
    private final int finalPriceCts;
    private final int vatCts;
    private final int discountCts;
    private final String extReference;
    private final String currencyCode;

    private final List<String> tags;
    private final UUID subscriptionId;
    private final PriceContainer.VatStatus vatStatus;

    public Ticket(@JsonProperty("id") @Column("id") int id,
                  @JsonProperty("uuid") @Column("uuid") String uuid,
                  @JsonProperty("publicUuid") @Column("public_uuid") UUID publicUuid,
                  @JsonProperty("creation") @Column("creation") ZonedDateTime creation,
                  @JsonProperty("categoryId") @Column("category_id") Integer categoryId,
                  @JsonProperty("status") @Column("status") String status,
                  @JsonProperty("eventId") @Column("event_id") int eventId,
                  @JsonProperty("ticketsReservationId") @Column("tickets_reservation_id") String ticketsReservationId,
                  @JsonProperty("fullName") @Column("full_name") String fullName,
                  @JsonProperty("firstName") @Column("first_name") String firstName,
                  @JsonProperty("lastName") @Column("last_name") String lastName,
                  @JsonProperty("email") @Column("email_address") String email,
                  @JsonProperty("lockedAssignment") @Column("locked_assignment") boolean lockedAssignment,
                  @JsonProperty("userLanguage") @Column("user_language") String userLanguage,
                  @JsonProperty("srcPriceCts") @Column("src_price_cts") int srcPriceCts,
                  @JsonProperty("finalPriceCts") @Column("final_price_cts") int finalPriceCts,
                  @JsonProperty("vatCts") @Column("vat_cts") int vatCts,
                  @JsonProperty("discountCts") @Column("discount_cts") int discountCts,
                  @JsonProperty("extReference") @Column("ext_reference") String extReference,
                  @JsonProperty("currencyCode") @Column("currency_code") String currencyCode,
                  @JsonProperty("tags") @Column("tags") @Array List<String> tags,
                  @JsonProperty("subscriptionId") @Column("subscription_id_fk") UUID subscriptionId,
                  @JsonProperty("vatStatus") @Column("vat_status") PriceContainer.VatStatus vatStatus) {
        this.id = id;
        this.uuid = uuid;
        this.publicUuid = publicUuid;
        this.creation = creation;
        this.categoryId = categoryId;
        this.eventId = eventId;

        this.userLanguage = userLanguage;
        this.status = TicketStatus.valueOf(status);
        this.ticketsReservationId = ticketsReservationId;
        this.fullName = Optional.ofNullable(fullName).orElse("");
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = Optional.ofNullable(email).orElse("");
        this.lockedAssignment = lockedAssignment;
        this.srcPriceCts = srcPriceCts;
        this.finalPriceCts = finalPriceCts;
        this.vatCts = vatCts;
        this.discountCts = discountCts;
        this.extReference = extReference;
        this.currencyCode = currencyCode;
        this.tags = tags;
        this.subscriptionId = subscriptionId;
        this.vatStatus = vatStatus;
    }
    
    @Override
    public boolean getAssigned() {
        return (StringUtils.isNotBlank(fullName) || (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName))) && StringUtils.isNotBlank(email);
    }
    
    public boolean getLockedAssignment() {
        return lockedAssignment;
    }

    /**
     * The code is composed with:
     * 
     * <pre>uuid + '/' + hmac_sha256_base64((ticketsReservationId + '/' + uuid + '/' + fullName + '/' + email), eventKey)</pre>
     * 
     * @param eventKey
     * @return
     */
    public String ticketCode(String eventKey, boolean caseInsensitive) {
        return uuid + '/' + hmacTicketInfo(eventKey, caseInsensitive);
    }

    public String hmacTicketInfo(String eventKey, boolean caseInsensitive) {
        return generateHmacTicketInfo(eventKey, caseInsensitive, getFullName(), email, ticketsReservationId, uuid);
    }

    public boolean hasBeenSold() {
        return SOLD_STATUSES.contains(status);
    }

    @Override
    public boolean isCheckedIn() {
        return status == TicketStatus.CHECKED_IN;
    }

    static String generateHmacTicketInfo(String eventKey, boolean caseInsensitive, String fullName, String email, String ticketsReservationId, String uuid) {
        var attendeeName = fullName;
        var attendeeEmail = email;
        if (caseInsensitive) {
            attendeeName = NameNormalizer.normalize(attendeeName);
            attendeeEmail = email.toLowerCase(Locale.ROOT);
        }
        return hmacSHA256Base64(eventKey, StringUtils.join(new String[]{ticketsReservationId , uuid, attendeeName, attendeeEmail}, '/'));
    }

    public static String hmacSHA256Base64(String key, String code) {
        return Base64.getEncoder().encodeToString(new HmacUtils(HmacAlgorithms.HMAC_SHA_256, key).hmac(code));
    }

    public String getFullName() {
        return (firstName != null && lastName != null) ? (firstName + " " + lastName) : fullName;
    }

    public String getFormattedFinalPrice() {
        return MonetaryUtil.formatCents(finalPriceCts, currencyCode);
    }

    public String getFormattedNetPrice() {
        return MonetaryUtil.formatCents(finalPriceCts - vatCts, currencyCode);
    }

    public Ticket withVatStatus(PriceContainer.VatStatus newVatStatus) {
        return new Ticket(
            id,
            uuid,
            publicUuid,
            creation,
            categoryId,
            status.name(),
            eventId,
            ticketsReservationId,
            fullName,
            firstName,
            lastName,
            email,
            lockedAssignment,
            userLanguage,
            srcPriceCts,
            finalPriceCts,
            vatCts,
            discountCts,
            extReference,
            currencyCode,
            tags,
            subscriptionId,
            newVatStatus
        );
    }
}
