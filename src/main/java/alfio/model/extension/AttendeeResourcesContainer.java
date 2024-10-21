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
package alfio.model.extension;

import alfio.model.modification.AttendeeResources;

public class AttendeeResourcesContainer {
    private final AttendeeResources attendeeResources;


    public AttendeeResourcesContainer(AttendeeResources attendeeResources) {
        this.attendeeResources = attendeeResources;
    }

    public String getTicketPdf() {
        return attendeeResources.ticketPdf();
    }

    public String getTicketQrCode() {
        return attendeeResources.ticketQrCode();
    }

    public String getGoogleWallet() {
        return attendeeResources.googleWallet();
    }

    public String getApplePass() {
        return attendeeResources.applePass();
    }
}
