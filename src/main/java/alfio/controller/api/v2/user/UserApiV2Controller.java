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

import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.controller.api.v2.model.ClientRedirect;
import alfio.controller.api.v2.model.PurchaseContextWithReservations;
import alfio.controller.api.v2.model.User;
import alfio.controller.form.UpdateProfileForm;
import alfio.controller.support.CustomBindingResult;
import alfio.manager.ExtensionManager;
import alfio.manager.TicketReservationManager;
import alfio.manager.i18n.MessageSourceManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.PublicUserManager;
import alfio.model.ContentLanguage;
import alfio.util.ErrorsCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/public/user")
@RequiredArgsConstructor
public class UserApiV2Controller {

    private final PublicUserManager publicUserManager;
    private final TicketReservationManager ticketReservationManager;
    private final ConfigurationManager configurationManager;
    private final ExtensionManager extensionManager;
    private final MessageSourceManager messageSourceManager;

    @GetMapping("/me")
    public ResponseEntity<User> getUserIdentity(Authentication principal) {
        if(principal != null) {
            return publicUserManager.findOptionalProfileForUser(principal)
                .map(userWithOptionalProfile -> {
                    var user = userWithOptionalProfile.getLeft();
                    return ResponseEntity.ok(new User(
                        user.getFirstName(),
                        user.getLastName(),
                        user.getEmailAddress(),
                        user.getType(),
                        userWithOptionalProfile.getRight().orElse(null)));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PostMapping("/me")
    public ResponseEntity<ValidatedResponse<User>> updateProfile(@RequestBody UpdateProfileForm update,
                                                                 BindingResult bindingResult,
                                                                 Principal principal) {
        if(principal != null) {

            boolean italianEInvoicingEnabled = configurationManager.isItalianEInvoicingEnabled(ConfigurationLevel.system());

            return publicUserManager.findOptionalEnabledUserByUsername(principal.getName())
                .map(u -> {
                    var customBindingResult = new CustomBindingResult(bindingResult);
                    // set email from original user to pass the validation
                    update.setEmail(u.getEmailAddress());
                    // enforce billing address validation if EInvoicing is enabled
                    update.formalValidation(customBindingResult, italianEInvoicingEnabled, italianEInvoicingEnabled);
                    if(!customBindingResult.hasErrors()) {
                        extensionManager.handleUserProfileValidation(update, bindingResult);
                    }
                    if(!customBindingResult.hasErrors()) {
                        var publicUserProfile = publicUserManager.updateProfile(u, update, italianEInvoicingEnabled);
                        if(publicUserProfile.isPresent()) {
                            var profile = publicUserProfile.get();
                            var updatedUser = new User(update.getFirstName(),
                                update.getLastName(),
                                u.getEmailAddress(),
                                u.getType(),
                                profile);
                            return ResponseEntity.ok(ValidatedResponse.toResponse(customBindingResult, updatedUser));
                        }
                        customBindingResult.reject(ErrorsCode.EMPTY_FIELD);
                    }
                    ValidatedResponse<User> body = ValidatedResponse.toResponse(customBindingResult, null);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
                }).orElseGet(() -> ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<ClientRedirect> deleteCurrentUser(Authentication authentication) {
        var alfioAuthentication = ((OpenIdAlfioAuthentication)authentication);

        if(publicUserManager.deleteUserProfile(alfioAuthentication)) {
            return redirectToIdpLogout(authentication);
        }
        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/authentication-enabled")
    public ResponseEntity<Boolean> userAuthenticationEnabled() {
        return ResponseEntity.ok(configurationManager.isPublicOpenIdEnabled());
    }

    @PostMapping("/logout")
    public ResponseEntity<ClientRedirect> logout(Authentication authentication) {
        return redirectToIdpLogout(authentication);
    }

    @GetMapping("/reservations")
    public ResponseEntity<List<PurchaseContextWithReservations>> getUserReservations(Principal principal) {
        if(principal != null) {
            var reservations = ticketReservationManager.loadReservationsForUser(principal);
            if(reservations.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
            }
            // "datetime.pattern"
            var messageSource = messageSourceManager.getRootMessageSource();
            var datePatternsMap = ContentLanguage.ALL_LANGUAGES.stream()
                .map(l -> Map.entry(l.getLocale(), messageSource.getMessage("datetime.pattern", null, l.getLocale())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            var results = reservations.stream()
                .collect(Collectors.groupingBy(p -> p.getPurchaseContextType().name() + "/" + p.getPurchaseContextPublicIdentifier()))
                .values().stream()
                .map(pc -> PurchaseContextWithReservations.from(pc, datePatternsMap))
                .collect(Collectors.toList());
            return ResponseEntity.ok(results);
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static ResponseEntity<ClientRedirect> redirectToIdpLogout(Authentication authentication) {
        if(authentication != null) {
            SecurityContextHolder.getContext().setAuthentication(null);
        }
        String redirectUrl = "/";
        if(authentication instanceof OpenIdAlfioAuthentication) {
            redirectUrl = ((OpenIdAlfioAuthentication) authentication).getIdpLogoutRedirectionUrl();
        }
        return ResponseEntity.ok(new ClientRedirect(redirectUrl));
    }
}
