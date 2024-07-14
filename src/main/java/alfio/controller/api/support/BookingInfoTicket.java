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
package alfio.controller.api.support;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class BookingInfoTicket {
    private final String uuid;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String fullName;
    private final String userLanguage;
    private final boolean assigned;
    private final boolean locked;
    private final boolean acquired;
    private final boolean cancellationEnabled;
    private final boolean sendMailEnabled;
    private final boolean downloadEnabled;
    private final List<AdditionalField> ticketFieldConfiguration;
    private final Map<String, String> formattedOnlineCheckInDate;
    private final boolean onlineEventStarted;

    public BookingInfoTicket(String uuid,
                             String firstName,
                             String lastName,
                             String email,
                             String fullName,
                             String userLanguage,
                             boolean assigned,
                             boolean locked,
                             boolean acquired,
                             boolean cancellationEnabled,
                             boolean sendMailEnabled,
                             boolean downloadEnabled,
                             List<AdditionalField> ticketFieldConfiguration,
                             Map<String, String> formattedOnlineCheckInDate,
                             boolean onlineEventStarted) {
        this.uuid = uuid;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.fullName = fullName;
        this.userLanguage = userLanguage;
        this.assigned = assigned;
        this.locked = locked;
        this.acquired = acquired;
        this.cancellationEnabled = cancellationEnabled;
        this.sendMailEnabled = sendMailEnabled;
        this.downloadEnabled = downloadEnabled;
        this.ticketFieldConfiguration = ticketFieldConfiguration;
        this.formattedOnlineCheckInDate = formattedOnlineCheckInDate;
        this.onlineEventStarted = onlineEventStarted;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getUuid() {
        return uuid;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isAssigned() {
        return assigned;
    }

    public boolean isAcquired() {
        return acquired;
    }

    public String getUserLanguage() {
        return userLanguage;
    }

    public boolean isLocked() {
        return locked;
    }

    public boolean isCancellationEnabled() {
        return cancellationEnabled;
    }

    public List<AdditionalField> getTicketFieldConfigurationBeforeStandard() {
        return ticketFieldConfiguration.stream().filter(AdditionalField::isBeforeStandardFields).collect(Collectors.toList());
    }

    public List<AdditionalField> getTicketFieldConfigurationAfterStandard() {
        return ticketFieldConfiguration.stream().filter(tv -> !tv.isBeforeStandardFields()).collect(Collectors.toList());
    }

    public Map<String, String> getFormattedOnlineCheckInDate() {
        return formattedOnlineCheckInDate;
    }

    public boolean isOnlineEventStarted() {
        return onlineEventStarted;
    }

    public boolean isSendMailEnabled() {
        return sendMailEnabled;
    }

    public boolean isDownloadEnabled() {
        return downloadEnabled;
    }
}
