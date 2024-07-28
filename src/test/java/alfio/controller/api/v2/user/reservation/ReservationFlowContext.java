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

import alfio.model.Event;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;

public class ReservationFlowContext {
    final Event event;
    final String userId;
    final UUID subscriptionId;
    final String subscriptionPin;
    final String publicUsername;
    final Integer publicUserId;
    final boolean checkInStationsEnabled;
    final boolean applyDiscount;
    private final Authentication authentication;
    private final Map<String, String> additionalParams;
    final boolean vatIncluded;

    ReservationFlowContext(Event event, String userId) {
        this(event, userId, null, null, null, null, true, false, Map.of(), true);
    }

    ReservationFlowContext(Event event, String userId, UUID subscriptionId, String subscriptionPin) {
        this(event, userId, subscriptionId, subscriptionPin, null, null, true, false, Map.of(), true);
    }

    ReservationFlowContext(Event event, String userId, UUID subscriptionId, String subscriptionPin, String publicUsername, Integer publicUserId, boolean checkInStationsEnabled, boolean applyDiscount) {
        this(event, userId, subscriptionId, subscriptionPin, publicUsername, publicUserId, checkInStationsEnabled, applyDiscount, Map.of(), true);
    }

    ReservationFlowContext(Event event, String userId, UUID subscriptionId, String subscriptionPin, String publicUsername, Integer publicUserId, boolean checkInStationsEnabled, boolean applyDiscount, Map<String, String> additionalParams, boolean vatIncluded) {
        this.event = event;
        this.userId = userId;
        this.subscriptionId = subscriptionId;
        this.subscriptionPin = subscriptionPin;
        this.publicUsername = publicUsername;
        this.publicUserId = publicUserId;
        if(publicUsername != null && publicUserId != null) {
            var authentication = Mockito.mock(OAuth2AuthenticationToken.class);
            when(authentication.getName()).thenReturn(publicUsername);
            this.authentication = authentication;
        } else {
            this.authentication = null;
        }
        this.checkInStationsEnabled = checkInStationsEnabled;
        this.applyDiscount = applyDiscount;
        this.additionalParams = additionalParams;
        this.vatIncluded = vatIncluded;
    }

    Principal getPublicUser() {
        return authentication;
    }

    Authentication getPublicAuthentication() {
        return authentication;
    }

    Map<String, String> getAdditionalParams() {
        return additionalParams;
    }
}
