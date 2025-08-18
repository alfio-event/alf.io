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
package alfio.manager.payment.custom.offline;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.EventAndOrganizationId;
import alfio.model.TicketCategory;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.PaymentProxy;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.EventRepository;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.Json;

@Component
public class CustomOfflineConfigurationManager {
    private final ConfigurationManager configurationManager;
    private final ConfigurationRepository configurationRepository;
    private final EventRepository eventRepository;

    public CustomOfflineConfigurationManager(
        ConfigurationManager configurationManager,
        ConfigurationRepository configurationRepository,
        EventRepository eventRepository
    ) {
        this.configurationManager = configurationManager;
        this.configurationRepository = configurationRepository;
        this.eventRepository = eventRepository;
    }


    public List<UserDefinedOfflinePaymentMethod> getOrganizationCustomOfflinePaymentMethods(int orgId) {
        return this.getOrganizationCustomOfflinePaymentMethodsIncludingDeleted(orgId)
            .stream()
            .filter(pm -> !pm.isDeleted())
            .toList();
    }

    public List<UserDefinedOfflinePaymentMethod> getOrganizationCustomOfflinePaymentMethodsIncludingDeleted(int orgId) {
        var paymentMethodRaws = configurationRepository
            .findByKeyAtOrganizationLevel(orgId, ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getValue());

        return paymentMethodRaws
            .map(cf -> cf.getValue())
            .map(v -> Json.fromJson(v, new TypeReference<List<UserDefinedOfflinePaymentMethod>>() {}))
            .orElse(new ArrayList<>());
    }

    public UserDefinedOfflinePaymentMethod getOrganizationCustomOfflinePaymentMethodById(
        int orgId,
        String paymentMethodId
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        return this.getOrganizationCustomOfflinePaymentMethods(orgId)
            .stream()
            .filter(pm -> pm.getPaymentMethodId().equals(paymentMethodId))
            .findAny()
            .orElseThrow(() -> new CustomOfflinePaymentMethodDoesNotExistException());
    }

    public void createOrganizationCustomOfflinePaymentMethod(
        int orgId,
        UserDefinedOfflinePaymentMethod paymentMethod
    ) throws CustomOfflinePaymentMethodAlreadyExistsException {
        var paymentMethods = this.getOrganizationCustomOfflinePaymentMethodsIncludingDeleted(orgId);

        var methodIdExists = paymentMethods
            .stream()
            .anyMatch(pm -> pm.getPaymentMethodId().equals(paymentMethod.getPaymentMethodId()));

        if(methodIdExists) {
            throw new CustomOfflinePaymentMethodAlreadyExistsException();
        }

        paymentMethods.add(paymentMethod);
        this.saveAndOverwriteOrganizationCustomOfflinePaymentMethods(orgId, paymentMethods);
    }

    public void updateOrganizationCustomOfflinePaymentMethod(
        int orgId,
        UserDefinedOfflinePaymentMethod paymentMethod
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        var paymentMethodsIncludingDeleted = this.getOrganizationCustomOfflinePaymentMethodsIncludingDeleted(orgId);

        paymentMethodsIncludingDeleted
            .stream()
            .filter(pm -> !pm.isDeleted() && pm.getPaymentMethodId().equals(paymentMethod.getPaymentMethodId()))
            .findAny()
            .orElseThrow(() -> new CustomOfflinePaymentMethodDoesNotExistException(
                "Passed payment method does not exist in the organization"
            ));

        var newPaymentMethods = paymentMethodsIncludingDeleted
            .stream()
            .filter(pm -> !pm.getPaymentMethodId().equals(paymentMethod.getPaymentMethodId()))
            .collect(Collectors.toList());

        newPaymentMethods.add(paymentMethod);
        this.saveAndOverwriteOrganizationCustomOfflinePaymentMethods(orgId, newPaymentMethods);
    }

    public void deleteOrganizationCustomOfflinePaymentMethod(
        int orgId,
        UserDefinedOfflinePaymentMethod paymentMethod
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        var paymentMethods = this.getOrganizationCustomOfflinePaymentMethods(orgId);

        if(paymentMethods.stream().noneMatch(pm -> pm.getPaymentMethodId().equals(paymentMethod.getPaymentMethodId()))) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "Passed payment method '"+paymentMethod.getPaymentMethodId()+"' does not exist in organization '"+orgId+"'"
            );
        }

        paymentMethods
            .stream()
            .filter(pm -> pm.getPaymentMethodId().equals(paymentMethod.getPaymentMethodId()))
            .forEach(UserDefinedOfflinePaymentMethod::setDeleted);

        this.saveAndOverwriteOrganizationCustomOfflinePaymentMethods(orgId, paymentMethods);
    }

    public List<UserDefinedOfflinePaymentMethod> getAllowedCustomOfflinePaymentMethodsForEvent(Event event) {
        var paymentMethods = getOrganizationCustomOfflinePaymentMethods(event.getOrganizationId());

        var allowedMethodIDsForEvent = configurationManager
            .getFor(ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS, ConfigurationLevel.event(event))
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<List<String>>() {}))
            .orElse(new ArrayList<>());

        return paymentMethods
            .stream()
            .filter(pm -> allowedMethodIDsForEvent.contains(pm.getPaymentMethodId()))
            .toList();
    }

    public void setAllowedCustomOfflinePaymentMethodsForEvent(
        Event event,
        List<String> paymentMethodIds
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        var orgPaymentMethods = this.getOrganizationCustomOfflinePaymentMethods(event.getOrganizationId());

        var passedNotInOrgIds = paymentMethodIds
            .stream()
            .filter(id ->
                orgPaymentMethods
                    .stream()
                    .noneMatch(orgPm -> orgPm.getPaymentMethodId().equals(id))
            )
            .toList();

        if(!passedNotInOrgIds.isEmpty()) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "Passed payment method ID(s) " + String.join(",", passedNotInOrgIds) + " do not exist in organization."
            );
        }

        Optional<Configuration> existing = configurationRepository.findByKeyAtEventLevel(
            event.getId(),
            event.getOrganizationId(),
            ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name()
        );

        if(existing.isPresent() && !existing.get().getValue().isEmpty()) {
            configurationRepository.updateEventLevel(
                event.getId(),
                event.getOrganizationId(),
                ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name(),
                Json.toJson(paymentMethodIds)
            );
        } else {
            configurationRepository.insertEventLevel(
                event.getOrganizationId(),
                event.getId(),
                ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name(),
                Json.toJson(paymentMethodIds),
                ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.getDescription()
            );
        }
    }

    public List<UserDefinedOfflinePaymentMethod> getDeniedPaymentMethodsByTicketCategory(
        Event event,
        alfio.model.TicketCategory category
    ) {
        var maybeDeniedPaymentMethodsJson = configurationManager.getFor(
            ConfigurationKeys.DENIED_CUSTOM_PAYMENTS,
            ConfigurationLevel.ticketCategory(
                new EventAndOrganizationId(event.getId(), event.getOrganizationId()), category.getId()
            )
        );

        if (maybeDeniedPaymentMethodsJson.isEmpty()) {
            return List.of();
        }

        var deniedPaymentMethodsJson = maybeDeniedPaymentMethodsJson.getValue().get();
        var deniedPaymentMethodIds = Json.fromJson(deniedPaymentMethodsJson, new TypeReference<List<String>>(){});

        return this.getOrganizationCustomOfflinePaymentMethods(event.getOrganizationId())
            .stream()
            .filter(pmItem ->
                deniedPaymentMethodIds.stream().anyMatch(blItem -> blItem.equals(pmItem.getPaymentMethodId()))
            )
            .toList();
    }

    public void setDeniedPaymentMethodsByTicketCategory(
        Event event,
        TicketCategory category,
        List<UserDefinedOfflinePaymentMethod> paymentMethods
    ) throws CustomOfflinePaymentMethodDoesNotExistException {
        var orgPaymentMethods = this.getOrganizationCustomOfflinePaymentMethods(event.getOrganizationId());

        var allInputPaymentMethodsExist = paymentMethods
            .stream()
            .allMatch(inPm ->
                orgPaymentMethods
                    .stream()
                    .anyMatch(orgPm -> orgPm.getPaymentMethodId().equals(inPm.getPaymentMethodId()))
            );

        if(!allInputPaymentMethodsExist) {
            throw new CustomOfflinePaymentMethodDoesNotExistException(
                "One or more of the passed payment methods do not exist in the organization."
            );
        }

        var currentDenied = configurationRepository.findByKeyAtCategoryLevel(
            event.getId(),
            event.getOrganizationId(),
            category.getId(),
            ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.name()
        );

        if(currentDenied.isPresent() && !currentDenied.get().getValue().isEmpty()) {
            configurationRepository.updateCategoryLevel(
                event.getId(),
                event.getOrganizationId(),
                category.getId(),
                ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.name(),
                Json.toJson(paymentMethods.stream().map(UserDefinedOfflinePaymentMethod::getPaymentMethodId).toList())
            );
        } else {
            configurationRepository.insertTicketCategoryLevel(
                event.getOrganizationId(),
                event.getId(),
                category.getId(),
                ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.name(),
                Json.toJson(paymentMethods.stream().map(UserDefinedOfflinePaymentMethod::getPaymentMethodId).toList()),
                ConfigurationKeys.DENIED_CUSTOM_PAYMENTS.getDescription()
            );
        }
    }

    public boolean isPaymentMethodCurrentlyUsedInActiveEvent(int orgId, UserDefinedOfflinePaymentMethod paymentMethod) {
        List<Integer> orgEventIdsWithCustomPayments = eventRepository
            .findByOrganizationIds(List.of(orgId))
            .stream()
            .filter(event -> !event.expired() && event.getAllowedPaymentProxies().contains(PaymentProxy.CUSTOM_OFFLINE))
            .map(Event::getId)
            .toList();

        if(orgEventIdsWithCustomPayments.isEmpty()) {
            return false;
        }

        return configurationRepository
            .findAllByEventsAndKey(ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS.name(), orgEventIdsWithCustomPayments)
            .stream()
            .map(config -> Json.fromJson(config.getValue(), new TypeReference<List<String>>() {}))
            .flatMap(List::stream)
            .anyMatch(id -> id.equals(paymentMethod.getPaymentMethodId()));
    }

    private void saveAndOverwriteOrganizationCustomOfflinePaymentMethods(
        int orgId,
        List<UserDefinedOfflinePaymentMethod> paymentMethods
    ) {
        var serialized = Json.toJson(paymentMethods);
        configurationManager.saveConfig(
            Configuration.from(orgId, ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS),
            serialized
        );
    }

    public static class CustomOfflinePaymentMethodAlreadyExistsException extends Exception {
        public CustomOfflinePaymentMethodAlreadyExistsException() {
            super();
        }
        public CustomOfflinePaymentMethodAlreadyExistsException(String message) {
            super(message);
        }
        public CustomOfflinePaymentMethodAlreadyExistsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public static class CustomOfflinePaymentMethodDoesNotExistException extends Exception {
        public CustomOfflinePaymentMethodDoesNotExistException() {
            super();
        }
        public CustomOfflinePaymentMethodDoesNotExistException(String message) {
            super(message);
        }
        public CustomOfflinePaymentMethodDoesNotExistException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    public static class CustomOfflinePaymentMethodIllegalDeletionStateException extends Exception {
        public CustomOfflinePaymentMethodIllegalDeletionStateException() {
            super();
        }
        public CustomOfflinePaymentMethodIllegalDeletionStateException(String message) {
            super(message);
        }
        public CustomOfflinePaymentMethodIllegalDeletionStateException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
