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
import org.apache.commons.lang3.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

@Getter
public class Ticket {

    public enum TicketStatus {
        FREE, PENDING, TO_BE_PAID, ACQUIRED, CANCELLED, CHECKED_IN, EXPIRED, INVALIDATED, RELEASED, PRE_RESERVED
    }

    private static final Set<TicketStatus> SOLD_STATUSES = EnumSet.of(TicketStatus.TO_BE_PAID, TicketStatus.ACQUIRED, TicketStatus.CANCELLED, TicketStatus.CHECKED_IN, TicketStatus.RELEASED);

    private final int id;
    private final String uuid;
    private final ZonedDateTime creation;
    private final Integer categoryId;
    private final int eventId;
    private final TicketStatus status;
    private final String ticketsReservationId;
    private final String fullName;
    private final String email;
    private final boolean lockedAssignment;
    private final String userLanguage;

    private final int srcPriceCts;
    private final int finalPriceCts;
    private final int vatCts;
    private final int discountCts;

    public Ticket(@Column("id") int id,
                  @Column("uuid") String uuid,
                  @Column("creation") ZonedDateTime creation,
                  @Column("category_id") Integer categoryId,
                  @Column("status") String status,
                  @Column("event_id") int eventId,
                  @Column("tickets_reservation_id") String ticketsReservationId,
                  @Column("full_name") String fullName,
                  @Column("email_address") String email,
                  @Column("locked_assignment") boolean lockedAssignment,
                  @Column("user_language") String userLanguage,
                  @Column("src_price_cts") int srcPriceCts,
                  @Column("final_price_cts") int finalPriceCts,
                  @Column("vat_cts") int vatCts,
                  @Column("discount_cts") int discountCts) {
        this.id = id;
        this.uuid = uuid;
        this.creation = creation;
        this.categoryId = categoryId;
        this.eventId = eventId;

        this.userLanguage = userLanguage;
        this.status = TicketStatus.valueOf(status);
        this.ticketsReservationId = ticketsReservationId;
        this.fullName = Optional.ofNullable(fullName).orElse("");
        this.email = Optional.ofNullable(email).orElse("");
        this.lockedAssignment = lockedAssignment;
        this.srcPriceCts = srcPriceCts;
        this.finalPriceCts = finalPriceCts;
        this.vatCts = vatCts;
        this.discountCts = discountCts;
    }
    
    public boolean getAssigned() {
        return StringUtils.isNotBlank(fullName) && StringUtils.isNotBlank(email);
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
    public String ticketCode(String eventKey) {
        String code = StringUtils.join(new String[]{ticketsReservationId , uuid, fullName, email}, '/');
        return uuid + '/' + hmacSHA256Base64(eventKey, code);
    }

    public boolean hasBeenSold() {
        return SOLD_STATUSES.contains(status);
    }

    public boolean isCheckedIn() {
        return status == TicketStatus.CHECKED_IN;
    }

    private static String hmacSHA256Base64(String key, String code) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(hmac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
        } catch(InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
