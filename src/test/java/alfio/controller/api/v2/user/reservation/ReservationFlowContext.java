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
package alfio.controller.api.v2.user.reservation;

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.model.Event;
import org.springframework.security.core.Authentication;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

class ReservationFlowContext {
    final Event event;
    final String userId;
    final UUID subscriptionId;
    final String subscriptionPin;
    final String publicUsername;
    final Integer publicUserId;
    final boolean checkInStationsEnabled;
    private final Authentication authentication;

    ReservationFlowContext(Event event, String userId) {
        this(event, userId, null, null, null, null, true);
    }

    ReservationFlowContext(Event event, String userId, UUID subscriptionId, String subscriptionPin) {
        this(event, userId, subscriptionId, subscriptionPin, null, null, true);
    }

    ReservationFlowContext(Event event, String userId, UUID subscriptionId, String subscriptionPin, String publicUsername, Integer publicUserId, boolean checkInStationsEnabled) {
        this.event = event;
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.subscriptionPin = subscriptionPin;
        this.publicUsername = publicUsername;
        this.publicUserId = publicUserId;
        if(publicUsername != null && publicUserId != null) {
            this.authentication = new OpenIdAlfioAuthentication(List.of(), "", publicUsername, publicUsername, "", true);
        } else {
            this.authentication = null;
        }
        this.checkInStationsEnabled = checkInStationsEnabled;
    }

    Principal getPublicUser() {
        return authentication;
    }

    Authentication getPublicAuthentication() {
        return authentication;
    }
}
