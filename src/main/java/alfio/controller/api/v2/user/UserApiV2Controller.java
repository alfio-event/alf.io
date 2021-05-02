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
package alfio.controller.api.v2.user;

import alfio.controller.api.v2.model.PurchaseContextWithReservations;
import alfio.controller.api.v2.model.User;
import alfio.manager.TicketReservationManager;
import alfio.manager.openid.OpenIdAuthenticationManager;
import alfio.manager.user.UserManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/public/user")
public class UserApiV2Controller {

    private final UserManager userManager;
    private final TicketReservationManager ticketReservationManager;
    private final OpenIdAuthenticationManager openIdAuthenticationManager;

    public UserApiV2Controller(UserManager userManager,
                               TicketReservationManager ticketReservationManager,
                               @Qualifier("publicOpenIdAuthenticationManager") OpenIdAuthenticationManager openIdAuthenticationManager) {
        this.userManager = userManager;
        this.ticketReservationManager = ticketReservationManager;
        this.openIdAuthenticationManager = openIdAuthenticationManager;
    }

    @GetMapping("/me")
    public ResponseEntity<User> getUserIdentity(Principal principal) {
        if(principal != null) {
            return userManager.findOptionalEnabledUserByUsername(principal.getName())
                .map(u -> {
                    var userProfileOptional = userManager.findOptionalProfileForUser(u.getId());
                    return ResponseEntity.ok(new User(u.getFirstName(), u.getLastName(), u.getEmailAddress(), userProfileOptional.orElse(null)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/authentication-enabled")
    public ResponseEntity<Boolean> userAuthenticationEnabled() {
        return ResponseEntity.ok(openIdAuthenticationManager.isEnabled());
    }

    @PostMapping("/logout")
    public ResponseEntity<Boolean> logout(Principal principal) {
        if(principal != null) {
            SecurityContextHolder.getContext().setAuthentication(null);
        }
        return ResponseEntity.ok(true);
    }

    @GetMapping("/reservations")
    public ResponseEntity<List<PurchaseContextWithReservations>> getUserReservations(Principal principal) {
        if(principal != null) {
            var reservations = ticketReservationManager.loadReservationsForUser(principal);
            if(reservations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
            var results = reservations.stream()
                .collect(Collectors.groupingBy(p -> p.getPurchaseContextType().name() + "/" + p.getPurchaseContextPublicIdentifier()))
                .values().stream()
                .map(PurchaseContextWithReservations::from)
                .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
