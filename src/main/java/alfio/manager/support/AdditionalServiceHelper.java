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
package alfio.manager.support;

import alfio.controller.api.support.AdditionalField;
import alfio.controller.api.support.AdditionalServiceWithData;
import alfio.controller.api.support.Field;
import alfio.manager.AdditionalServiceManager;
import alfio.manager.PurchaseContextFieldManager;
import alfio.model.*;
import alfio.repository.PurchaseContextFieldRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static alfio.controller.api.support.BookingInfoTicketLoader.fromFieldDescriptions;
import static alfio.model.FieldConfigurationDescriptionAndValue.isBeforeStandardFields;
import static java.util.stream.Collectors.groupingBy;

@Component
public class AdditionalServiceHelper {

    private final AdditionalServiceManager additionalServiceManager;
    private final PurchaseContextFieldRepository purchaseContextFieldRepository;
    private final PurchaseContextFieldManager purchaseContextFieldManager;

    public AdditionalServiceHelper(AdditionalServiceManager additionalServiceManager,
                                   PurchaseContextFieldRepository purchaseContextFieldRepository,
                                   PurchaseContextFieldManager purchaseContextFieldManager) {
        this.additionalServiceManager = additionalServiceManager;
        this.purchaseContextFieldRepository = purchaseContextFieldRepository;
        this.purchaseContextFieldManager = purchaseContextFieldManager;
    }

    public List<AdditionalServiceWithData> getAdditionalServicesWithData(PurchaseContext purchaseContext,
                                                                         List<AdditionalServiceItem> additionalServiceItems,
                                                                         Map<Integer, List<AdditionalServiceFieldValue>> valuesByItemId,
                                                                         Map<Long, List<PurchaseContextFieldDescription>> descriptionsByTicketFieldId, List<Ticket> tickets) {
        if (purchaseContext.ofType(PurchaseContext.PurchaseContextType.event) && ((Event)purchaseContext).supportsLinkedAdditionalServices()) {
            var event = ((Event)purchaseContext);
            if (!additionalServiceItems.isEmpty()) {
                var additionalServiceIds = additionalServiceItems.stream().map(AdditionalServiceItem::getAdditionalServiceId).collect(Collectors.toList());
                var additionalItemDescriptionsById = additionalServiceManager.getDescriptionsByAdditionalServiceIds(additionalServiceIds);
                var additionalItemTypeById = additionalServiceManager.getTypeByIds(additionalServiceIds);
                var additionalFieldsById = purchaseContextFieldRepository.findAdditionalFieldsForEvent(event.getId()).stream()
                    .filter(f -> f.getContext() == PurchaseContextFieldConfiguration.Context.ADDITIONAL_SERVICE && additionalServiceIds.contains(f.getAdditionalServiceId()))
                    .collect(Collectors.groupingBy(PurchaseContextFieldConfiguration::getAdditionalServiceId));
                return additionalServiceItems.stream()
                    .map(as -> {
                        var additionalItemTitle = additionalItemDescriptionsById.getOrDefault(as.getAdditionalServiceId(), Map.of())
                            .getOrDefault(AdditionalServiceText.TextType.TITLE, Collections.emptyMap());
                        var ticketId = as.getTicketId();
                        var itemValues = valuesByItemId.get(as.getId());
                        var fields = additionalFieldsById.getOrDefault(as.getAdditionalServiceId(), List.of()).stream()
                            .map(fieldConfiguration -> {
                                Optional<AdditionalServiceFieldValue> value = Optional.empty();
                                if (itemValues != null) {
                                    value = itemValues.stream()
                                        .filter(fv -> fv.getTicketId() == ticketId && fv.getFieldConfigurationId() == fieldConfiguration.getId())
                                        .findFirst();
                                }
                                var valueAsString = value.map(AdditionalServiceFieldValue::getValue).orElse("");
                                return AdditionalField.fromFieldConfiguration(fieldConfiguration,
                                    valueAsString,
                                    List.of(new Field(0, valueAsString)),
                                    isBeforeStandardFields(fieldConfiguration),
                                    fromFieldDescriptions(descriptionsByTicketFieldId.get(fieldConfiguration.getId())));
                            })
                            .collect(Collectors.toList());
                        var ticketUUID = tickets.stream().filter(t -> ticketId != null && t.getId() == ticketId)
                            .map(Ticket::getUuid)
                            .findFirst()
                            .orElse(null);
                        return new AdditionalServiceWithData(additionalItemTitle, as.getId(), as.getAdditionalServiceId(), ticketUUID, fields, additionalItemTypeById.get(as.getAdditionalServiceId()));
                    })
                    .sorted(Comparator.comparing(AdditionalServiceWithData::getServiceId))
                    .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    public List<AdditionalServiceWithData> findForTicket(Ticket ticket, Event event) {
        if (!event.supportsLinkedAdditionalServices()) {
            return List.of();
        }
        var additionalServiceItems = additionalServiceManager.findItemsForTicket(ticket);
        Map<Integer, List<AdditionalServiceFieldValue>> additionalServicesByItemId = additionalServiceItems.isEmpty() ? Map.of() :
            purchaseContextFieldRepository.findAdditionalServicesValueByItemIds(additionalServiceItems.stream().map(AdditionalServiceItem::getId).collect(Collectors.toList()))
                .stream().collect(groupingBy(AdditionalServiceFieldValue::getAdditionalServiceItemId));
        var descriptionsByTicketFieldId = purchaseContextFieldManager.findDescriptionsGroupedByFieldId(event);
        return getAdditionalServicesWithData(event, additionalServiceItems, additionalServicesByItemId, descriptionsByTicketFieldId, List.of(ticket));
    }
}
