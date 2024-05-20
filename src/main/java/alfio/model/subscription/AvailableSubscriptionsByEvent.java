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

import alfio.model.FieldNameAndValue;
import alfio.util.Json;
import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.List;
import java.util.UUID;

public class AvailableSubscriptionsByEvent {
    private final int eventId;
    private final int organizationId;
    private final UUID subscriptionId;
    private final UUID descriptorId;
    private final String emailAddress;
    private final String firstName;
    private final String lastName;
    private final String userLanguage;
    private final String reservationEmail;
    private final List<FieldNameAndValue> additionalFields;
    private final List<Integer> compatibleCategoryIds;

    public AvailableSubscriptionsByEvent(@Column("event_id") int eventId,
                                         @Column("organization_id") int organizationId,
                                         @Column("subscription_id") UUID subscriptionId,
                                         @Column("descriptor_id") UUID descriptorId,
                                         @Column("email_address") String emailAddress,
                                         @Column("first_name") String firstName,
                                         @Column("last_name") String lastName,
                                         @Column("user_language") String userLanguage,
                                         @Column("reservation_email") String reservationEmail,
                                         @Column("additional_fields") String additionalFieldsAsString,
                                         @Column("compatible_categories") String compatibleCategories) {
        this.eventId = eventId;
        this.organizationId = organizationId;
        this.subscriptionId = subscriptionId;
        this.descriptorId = descriptorId;
        this.emailAddress = emailAddress;
        this.firstName = firstName;
        this.lastName = lastName;
        this.userLanguage = userLanguage;
        this.reservationEmail = reservationEmail;
        if (additionalFieldsAsString != null) {
            this.additionalFields = Json.fromJson(additionalFieldsAsString, new TypeReference<>() {});
        } else {
            this.additionalFields = List.of();
        }
        this.compatibleCategoryIds = compatibleCategories != null ? Json.fromJson(compatibleCategories, new TypeReference<>() {}) : List.of();
    }

    public int getEventId() {
        return eventId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getDescriptorId() {
        return descriptorId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUserLanguage() {
        return userLanguage;
    }

    public int getOrganizationId() {
        return organizationId;
    }

    public String getReservationEmail() {
        return reservationEmail;
    }

    public List<FieldNameAndValue> getAdditionalFields() {
        return additionalFields;
    }

    public List<Integer> getCompatibleCategoryIds() {
        return compatibleCategoryIds;
    }
}
