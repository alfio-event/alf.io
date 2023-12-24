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
import alfio.controller.form.UpdateProfileForm;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.ExtensionManager;
import alfio.model.PurchaseContext;
import alfio.model.PurchaseContextFieldConfiguration;
import alfio.model.TicketReservationAdditionalInfo;
import alfio.model.TicketReservationInvoicingAdditionalInfo;
import alfio.model.extension.AdditionalInfoItem;
import alfio.model.user.AdditionalInfoWithLabel;
import alfio.model.user.PublicUserProfile;
import alfio.model.user.User;
import alfio.repository.PurchaseContextFieldRepository;
import alfio.repository.user.UserRepository;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.security.Principal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Component
@Transactional
@AllArgsConstructor
public class PublicUserManager {
    private final UserRepository userRepository;
    private final ExtensionManager extensionManager;
    private final UserManager userManager;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;

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
                                                     UpdateProfileForm update,
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
            updateExistingAdditionalData(currentAdditionalData, update)
        ));
        return userRepository.loadUserProfile(userId);
    }

    private Map<String, AdditionalInfoWithLabel> updateExistingAdditionalData(Map<String, AdditionalInfoWithLabel> existingData,
                                                                              UpdateProfileForm form) {
        if(existingData == null || existingData.isEmpty() || !form.hasAdditionalInfo()) {
            return Map.of();
        }

        var additionalInfoMap = form.getAdditionalInfo();
        return existingData.entrySet().stream()
            .map(entry -> {
                var existingAdditionalInfoWithLabel = entry.getValue();
                var newVal = additionalInfoMap.containsKey(entry.getKey()) ? List.of(additionalInfoMap.get(entry.getKey())) : existingAdditionalInfoWithLabel.getValues();
                return Map.entry(entry.getKey(), new AdditionalInfoWithLabel(existingAdditionalInfoWithLabel.getLabel(), newVal));
            })
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, AdditionalInfoWithLabel> buildAdditionalInfoWithLabels(PublicUserProfile existingProfile, PurchaseContext purchaseContext, UpdateTicketOwnerForm form) {
        var event = purchaseContext.event().orElseThrow();
        var fields = purchaseContextFieldRepository.findAdditionalFieldsOfTypeForEvent(event.getId(), "input:text");
        var fieldsById = fields.stream().collect(toMap(PurchaseContextFieldConfiguration::getId, Function.identity()));
        var userLanguage = form.getUserLanguage();
        var filteredItems = extensionManager.filterAdditionalInfoToSave(purchaseContext, form.getAdditional(), existingProfile);
        final Map<String, AdditionalInfoItem> filteredItemsByKey;
        filteredItemsByKey = filteredItems.map(additionalInfoItems -> additionalInfoItems.stream().collect(toMap(AdditionalInfoItem::getKey, Function.identity())))
            .orElse(null);
        var labels = purchaseContextFieldRepository.findDescriptionsForLocale(event.getId(), userLanguage).stream()
            .filter(f -> fieldsById.containsKey(f.getFieldConfigurationId()))
            .map(f -> Map.entry(fieldsById.get(f.getFieldConfigurationId()).getName(), f))
            .filter(f -> filteredItemsByKey == null || filteredItemsByKey.containsKey(f.getKey()))
            .map(e -> {
                if(filteredItemsByKey != null) {
                    return Map.entry(e.getKey(), filteredItemsByKey.get(e.getKey()).getLabel());
                }
                return Map.entry(e.getKey(), e.getValue().getLabelDescription());
            })
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, AdditionalInfoWithLabel> result = new HashMap<>(Optional.ofNullable(existingProfile).map(PublicUserProfile::getAdditionalData).orElse(Map.of()));
        // override existing values (if any) with new values.
        result.putAll(form.getAdditional().entrySet().stream()
            .filter(e -> labels.containsKey(e.getKey()))
            .map(e -> {
                var label = labels.get(e.getKey());
                return Map.entry(e.getKey(), new AdditionalInfoWithLabel(Map.of(userLanguage, label), e.getValue()));
            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        return result;
    }

    public Map<String, AdditionalInfoWithLabel> buildAdditionalInfoWithLabels(Authentication principal, PurchaseContext purchaseContext, UpdateTicketOwnerForm form) {
        var optionalExistingProfile = findOptionalProfileForUser(principal).flatMap(Pair::getValue);
        return buildAdditionalInfoWithLabels(optionalExistingProfile.orElse(null), purchaseContext, form);
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

    public void persistProfileForPublicUser(Principal principal,
                                            Object form,
                                            BindingResult bindingResult,
                                            TicketReservationAdditionalInfo reservationAdditionalInfo,
                                            Map<String, AdditionalInfoWithLabel> userAdditionalData) {
        if(principal == null) {
            return;
        }
        extensionManager.handleUserProfileValidation(form, bindingResult);
        if(!bindingResult.hasErrors()) {
            userRepository.findIdByUserName(principal.getName())
                .ifPresent(id -> userRepository.persistUserProfile(id,
                    reservationAdditionalInfo.getBillingAddressCompany(),
                    reservationAdditionalInfo.getBillingAddressLine1(),
                    reservationAdditionalInfo.getBillingAddressLine2(),
                    reservationAdditionalInfo.getBillingAddressZip(),
                    reservationAdditionalInfo.getBillingAddressCity(),
                    reservationAdditionalInfo.getBillingAddressState(),
                    reservationAdditionalInfo.getBillingAddressCountry(),
                    reservationAdditionalInfo.getVatNr(),
                    reservationAdditionalInfo.getInvoicingAdditionalInfo(),
                    userAdditionalData
                ));
        }
    }
}
