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
package alfio.manager;

import alfio.controller.form.AdditionalFieldsContainer;
import alfio.model.*;
import alfio.model.PurchaseContext.PurchaseContextType;
import alfio.model.PurchaseContextFieldConfiguration.Context;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.modification.EventModification;
import alfio.model.modification.TicketFieldDescriptionModification;
import alfio.model.result.ValidationResult;
import alfio.model.subscription.SubscriptionDescriptor;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.PurchaseContextFieldRepository;
import alfio.util.Json;
import alfio.util.MonetaryUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static alfio.util.Validator.validateAdditionalFields;

@Component
@Transactional
public class PurchaseContextFieldManager {
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final AdditionalServiceRepository additionalServiceRepository;

    public PurchaseContextFieldManager(PurchaseContextFieldRepository purchaseContextFieldRepository,
                                       AdditionalServiceRepository additionalServiceRepository) {
        this.purchaseContextFieldRepository = purchaseContextFieldRepository;
        this.additionalServiceRepository = additionalServiceRepository;
    }

    public Map<Long, List<PurchaseContextFieldDescription>> findDescriptionsGroupedByFieldId(PurchaseContext purchaseContext) {
        return purchaseContextFieldRepository.findAllDescriptions(eventIdOrNull(purchaseContext), descriptorIdOrNull(purchaseContext))
            .stream()
            .collect(Collectors.groupingBy(PurchaseContextFieldDescription::getFieldConfigurationId));
    }

    public Map<Integer, List<PurchaseContextFieldValue>> findAllValuesByTicketId(Integer ticketId) {
        return purchaseContextFieldRepository.findAllValuesByTicketIds(List.of(ticketId))
            .stream()
            .collect(Collectors.groupingBy(PurchaseContextFieldValue::getTicketId));
    }

    public List<PurchaseContextFieldConfiguration> findAdditionalFields(PurchaseContext purchaseContext) {
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            return purchaseContextFieldRepository.findAdditionalFieldsForEvent(((Event) purchaseContext).getId());
        } else{
            return purchaseContextFieldRepository.findAdditionalFieldsForSubscriptionDescriptor(((SubscriptionDescriptor) purchaseContext).getId());
        }
    }

    public void insertAdditionalField(PurchaseContext purchaseContext, EventModification.AdditionalField f, int order) {
        String serializedRestrictedValues = toSerializedRestrictedValues(f);
        int additionalServiceId = -1;
        Context context;
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            var event = (Event) purchaseContext;
            Optional<EventModification.AdditionalService> linkedAdditionalService = Optional.ofNullable(f.getLinkedAdditionalService());
            additionalServiceId = linkedAdditionalService.map(as -> Optional.ofNullable(as.getId()).orElseGet(() -> findAdditionalService(event, as, event.getCurrency()))).orElse(-1);
            context = linkedAdditionalService.isPresent() ? Context.ADDITIONAL_SERVICE : Context.ATTENDEE;
        } else {
            context = Context.SUBSCRIPTION;
        }

        long configurationId = purchaseContextFieldRepository.insertConfiguration(eventIdOrNull(purchaseContext), purchaseContext.getOrganizationId(), descriptorIdOrNull(purchaseContext), f.getName(), order, f.getType(), serializedRestrictedValues,
            f.getMaxLength(), f.getMinLength(), f.isRequired(), context, additionalServiceId, generateJsonForList(f.getLinkedCategoriesIds())).getKey();
        f.getDescription().forEach((locale, value) -> purchaseContextFieldRepository.insertDescription(configurationId, locale, Json.GSON.toJson(value), purchaseContext.getOrganizationId()));
    }

    public void updateAdditionalField(long id, EventModification.UpdateAdditionalField f, int organizationId) {
        String serializedRestrictedValues = toSerializedRestrictedValues(f);
        purchaseContextFieldRepository.updateField(id, f.isRequired(), !f.isReadOnly(), serializedRestrictedValues, toSerializedDisabledValues(f), generateJsonForList(f.getLinkedCategoriesIds()));
        f.getDescription().forEach((locale, value) -> {
            String val = Json.GSON.toJson(value.getDescription());
            if(0 == purchaseContextFieldRepository.updateDescription(id, locale, val)) {
                purchaseContextFieldRepository.insertDescription(id, locale, val, organizationId);
            }
        });
    }

    public ValidationResult validateAndAddField(PurchaseContext purchaseContext,
                                                EventModification.AdditionalField field,
                                                Errors errors) {
        List<PurchaseContextFieldConfiguration> fields;
        if (purchaseContext.ofType(PurchaseContextType.event)) {
            fields = purchaseContextFieldRepository.findAdditionalFieldsForEvent(((Event) purchaseContext).getId());
        } else {
            fields = purchaseContextFieldRepository.findAdditionalFieldsForSubscriptionDescriptor(((SubscriptionDescriptor) purchaseContext).getId());
        }
        return validateAdditionalFields(fields, field, errors)
            .ifSuccess(() -> addAdditionalField(purchaseContext, field));
    }

    public void addAdditionalField(PurchaseContext purchaseContext, EventModification.AdditionalField field) {
        if (field.isUseDefinedOrder()) {
            insertAdditionalField(purchaseContext, field, field.getOrder());
        } else {
            Integer order = purchaseContextFieldRepository.findMaxOrderValue(eventIdOrNull(purchaseContext), descriptorIdOrNull(purchaseContext));
            insertAdditionalField(purchaseContext, field, order == null ? 0 : order + 1);
        }
    }

    public void deleteAdditionalField(long ticketFieldConfigurationId) {
        purchaseContextFieldRepository.deleteValues(ticketFieldConfigurationId);
        purchaseContextFieldRepository.deleteDescription(ticketFieldConfigurationId);
        purchaseContextFieldRepository.deleteField(ticketFieldConfigurationId);
    }

    public void swapAdditionalFieldPosition(long id1, long id2) {
        PurchaseContextFieldConfiguration field1 = purchaseContextFieldRepository.findById(id1);
        PurchaseContextFieldConfiguration field2 = purchaseContextFieldRepository.findById(id2);
        purchaseContextFieldRepository.updateFieldOrder(id1, field2.getOrder());
        purchaseContextFieldRepository.updateFieldOrder(id2, field1.getOrder());
    }

    public void setAdditionalFieldPosition(long id, int newPosition) {
        purchaseContextFieldRepository.updateFieldOrder(id, newPosition);
    }

    private Integer findAdditionalService(Event event, EventModification.AdditionalService as, String currencyCode) {
        ZoneId utc = Clock.systemUTC().getZone();
        int eventId = event.getId();

        ZoneId eventZoneId = event.getZoneId();

        String checksum = new AdditionalService(0, eventId, as.isFixPrice(), as.getOrdinal(), as.getAvailableQuantity(),
            as.getMaxQtyPerOrder(),
            as.getInception().toZonedDateTime(eventZoneId).withZoneSameInstant(utc),
            as.getExpiration().toZonedDateTime(eventZoneId).withZoneSameInstant(utc),
            as.getVat(),
            as.getVatType(),
            Optional.ofNullable(as.getPrice()).map(p -> MonetaryUtil.unitToCents(p, currencyCode)).orElse(0),
            as.getType(),
            as.getSupplementPolicy(),
            currencyCode, null).getChecksum();
        return additionalServiceRepository.loadAllForEvent(eventId).stream().filter(as1 -> as1.getChecksum().equals(checksum)).findFirst().map(AdditionalService::getId).orElse(null);
    }

    public void updateFieldDescriptions(Map<String, TicketFieldDescriptionModification> descriptions, int organizationId) {
        descriptions.forEach((locale, value) -> {
            String description = Json.GSON.toJson(value.getDescription());
            if(0 == purchaseContextFieldRepository.updateDescription(value.getTicketFieldConfigurationId(), locale, description)) {
                purchaseContextFieldRepository.insertDescription(value.getTicketFieldConfigurationId(), locale, description, organizationId);
            }
        });
    }

    private static String toSerializedRestrictedValues(EventModification.WithRestrictedValues f) {
        return EventCreationRequest.WITH_RESTRICTED_VALUES.contains(f.getType()) ? generateJsonForList(f.getRestrictedValuesAsString()) : null;
    }

    private static String toSerializedDisabledValues(EventModification.WithRestrictedValues f) {
        return EventCreationRequest.WITH_RESTRICTED_VALUES.contains(f.getType()) ? generateJsonForList(f.getDisabledValuesAsString()) : null;
    }

    private static String generateJsonForList(Collection<?> values) {
        return CollectionUtils.isNotEmpty(values) ? Json.GSON.toJson(values) : null;
    }

    private static Integer eventIdOrNull(PurchaseContext purchaseContext) {
        return purchaseContext.ofType(PurchaseContextType.event) ? ((Event) purchaseContext).getId() : null;
    }

    private static UUID descriptorIdOrNull(PurchaseContext purchaseContext) {
        return purchaseContext.ofType(PurchaseContextType.subscription) ? ((SubscriptionDescriptor) purchaseContext).getId() : null;
    }

    public List<RestrictedValueStats> retrieveStats(long id) {
        return purchaseContextFieldRepository.retrieveStats(id);
    }

    // reservation-related methods
    public void updateFieldsForReservation(AdditionalFieldsContainer form,
                                           PurchaseContext purchaseContext,
                                           Integer ticketId,
                                           UUID subscriptionId) {
        purchaseContextFieldRepository.updateOrInsert(form.getAdditional(), purchaseContext, ticketId, subscriptionId);
    }
}
