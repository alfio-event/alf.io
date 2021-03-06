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
import alfio.controller.form.ContactAndTicketsForm;
import alfio.controller.support.CustomBindingResult;
import alfio.manager.TicketReservationManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.util.ErrorsCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/public/user")
@RequiredArgsConstructor
public class UserApiV2Controller {

    private final UserManager userManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;

    @GetMapping("/me")
    public ResponseEntity<User> getUserIdentity(Principal principal) {
        if(principal != null) {
            return userManager.findOptionalEnabledUserByUsername(principal.getName())
                .map(u -> {
                    var userProfileOptional = userManager.findOptionalProfileForUser(u.getId());
                    return ResponseEntity.ok(new User(
                        u.getFirstName(),
                        u.getLastName(),
                        u.getEmailAddress(),
                        userProfileOptional.orElse(null)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/me")
    public ResponseEntity<ValidatedResponse<User>> updateProfile(@RequestBody ContactAndTicketsForm update,
                                                                 BindingResult bindingResult,
                                                                 Principal principal) {
        if(principal != null) {

            boolean italianEInvoicingEnabled = configurationManager.isItalianEInvoicingEnabled(ConfigurationLevel.system());

            return ResponseEntity.of(userManager.findOptionalEnabledUserByUsername(principal.getName())
                .map(u -> {
                    var customBindingResult = new CustomBindingResult(bindingResult);
                    // set email from original user to pass the validation
                    update.setEmail(u.getEmailAddress());
                    update.formalValidation(customBindingResult, italianEInvoicingEnabled);
                    if(!customBindingResult.hasErrors()) {
                        var publicUserProfile = userManager.updateProfile(u, update, italianEInvoicingEnabled);
                        if(publicUserProfile.isPresent()) {
                            var profile = publicUserProfile.get();
                            var updatedUser = new User(update.getFirstName(),
                                update.getLastName(),
                                u.getEmailAddress(),
                                profile);
                            return ValidatedResponse.toResponse(customBindingResult, updatedUser);
                        }
                        customBindingResult.reject(ErrorsCode.EMPTY_FIELD);
                    }
                    return ValidatedResponse.toResponse(customBindingResult, null);
                }));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @GetMapping("/authentication-enabled")
    public ResponseEntity<Boolean> userAuthenticationEnabled() {
        return ResponseEntity.ok(configurationManager.isPublicOpenIdEnabled());
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
