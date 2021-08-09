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
package alfio.manager.user;


import alfio.config.authentication.support.OpenIdAlfioAuthentication;
import alfio.controller.form.ContactAndTicketsForm;
import alfio.manager.ExtensionManager;
import alfio.model.TicketReservationInvoicingAdditionalInfo;
import alfio.model.user.PublicUserProfile;
import alfio.model.user.User;
import alfio.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional
@AllArgsConstructor
public class PublicUserManager {
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;
    private final UserManager userManager;

    public boolean deleteUserProfile(OpenIdAlfioAuthentication authentication) {
        return userManager.findOptionalEnabledUserByUsername(authentication.getName())
            .filter(u -> u.getType() == User.Type.PUBLIC)
            .map(user -> {
                userRepository.deleteUserProfile(user.getId());
                userRepository.invalidatePublicUser(user.getId(), UUID.randomUUID() + "@deleted.alf.io");
                extensionManager.handlePublicUserDelete(authentication, user);
                return true;
            }).orElse(false);
    }

    public Optional<Pair<User, Optional<PublicUserProfile>>> findOptionalProfileForUser(Authentication authentication) {
        return userManager.findOptionalEnabledUserByUsername(authentication.getName())
            .map(user -> Pair.of(user, userRepository.loadUserProfile(user.getId())));
    }

    public Optional<PublicUserProfile> updateProfile(User original,
                                                     ContactAndTicketsForm update,
                                                     boolean italianEInvoicingEnabled) {
        // update user
        int userId = original.getId();
        userRepository.update(userId, original.getUsername(), update.getFirstName(), update.getLastName(), original.getEmailAddress(), original.getDescription());
        var currentAdditionalData = userRepository.loadUserProfile(userId).map(PublicUserProfile::getAdditionalData).orElse(Map.of());
        Validate.isTrue(1 == userRepository.persistUserProfile(userId,
            update.getBillingAddressCompany(),
            update.getBillingAddressLine1(),
            update.getBillingAddressLine2(),
            update.getBillingAddressZip(),
            update.getBillingAddressCity(),
            update.getBillingAddressState(),
            update.getVatCountryCode(),
            update.getVatNr(),
            getAdditionalInfo(update, italianEInvoicingEnabled),
            currentAdditionalData
        ));
        return userRepository.loadUserProfile(userId);
    }

    private TicketReservationInvoicingAdditionalInfo getAdditionalInfo(ContactAndTicketsForm update,
                                                                       boolean italianEInvoicingEnabled) {
        if(italianEInvoicingEnabled) {
            return new TicketReservationInvoicingAdditionalInfo(
                new TicketReservationInvoicingAdditionalInfo.ItalianEInvoicing(
                    update.getItalyEInvoicingFiscalCode(),
                    update.getItalyEInvoicingReferenceType(),
                    update.getItalyEInvoicingReferenceAddresseeCode(),
                    update.getItalyEInvoicingReferencePEC(),
                    update.isItalyEInvoicingSplitPayment())
            );
        }
        return new TicketReservationInvoicingAdditionalInfo(null);
    }

    public Optional<User> findOptionalEnabledUserByUsername(String username) {
        return userManager.findOptionalEnabledUserByUsername(username);
    }
}
