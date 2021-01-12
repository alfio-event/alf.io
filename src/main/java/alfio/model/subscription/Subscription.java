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
    private final String code;
    private final UUID subscriptionDescriptorId;
    private final String reservationId;
    private final int usageCount;
    private final int organizationId;
    private final ZonedDateTime creationTime;
    private final ZonedDateTime updateTime;

    public Subscription(@Column("id") UUID id,
                        @Column("first_name") String firstName,
                        @Column("last_name") String lastName,
                        @Column("email_address") String email,
                        @Column("code") String code,
                        @Column("subscription_descriptor_fk") UUID subscriptionDescriptorId,
                        @Column("reservation_id_fk") String reservationId,
                        @Column("usage_count") int usageCount,
                        @Column("organization_id_fk") int organizationId,
                        @Column("creation_ts") ZonedDateTime creationTime,
                        @Column("update_ts") ZonedDateTime updateTime) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.code = code;
        this.subscriptionDescriptorId = subscriptionDescriptorId;
        this.reservationId = reservationId;
        this.usageCount = usageCount;
        this.organizationId = organizationId;
        this.creationTime = creationTime;
        this.updateTime = updateTime;
    }
}
