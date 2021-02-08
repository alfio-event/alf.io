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
package alfio.model.subscription;

import alfio.util.PinGenerator;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.UUID;

@Getter
public class Subscription {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final UUID subscriptionDescriptorId;
    private final String reservationId;
    private final int usageCount;
    private final int organizationId;
    private final ZonedDateTime creationTime;
    private final ZonedDateTime updateTime;
    private final int srcPriceCts;
    private final int discountCts;
    private final String currency;

    public static final int PIN_LENGTH = 8;


    public Subscription(@Column("id") UUID id,
                        @Column("first_name") String firstName,
                        @Column("last_name") String lastName,
                        @Column("email_address") String email,
                        @Column("subscription_descriptor_fk") UUID subscriptionDescriptorId,
                        @Column("reservation_id_fk") String reservationId,
                        @Column("usage_count") int usageCount,
                        @Column("organization_id_fk") int organizationId,
                        @Column("creation_ts") ZonedDateTime creationTime,
                        @Column("update_ts") ZonedDateTime updateTime,
                        @Column("src_price_cts") int srcPriceCts,
                        @Column("discount_cts") int discountCts,
                        @Column("currency") String currency) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.subscriptionDescriptorId = subscriptionDescriptorId;
        this.reservationId = reservationId;
        this.usageCount = usageCount;
        this.organizationId = organizationId;
        this.creationTime = creationTime;
        this.updateTime = updateTime;
        this.srcPriceCts = srcPriceCts;
        this.discountCts = discountCts;
        this.currency = currency;
    }

    public boolean isValid(SubscriptionDescriptor subscriptionDescriptor) {
        if (subscriptionDescriptor.getMaxEntries() > 0) {
            //TODO check max entries
        }
        //TODO check date range validity
        return true;
    }

    public String getPin() {
        return PinGenerator.pinToPartialUuid(id.toString(), PIN_LENGTH);
    }
}
