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
package alfio.manager.payment.custom_offline;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.ConfigurationKeys;
import alfio.model.transaction.UserDefinedOfflinePaymentMethod;
import alfio.repository.system.ConfigurationRepository;
import alfio.util.Json;

@Component
public class CustomOfflineConfigurationManager {
    @Autowired
    ConfigurationManager configurationManager;
    @Autowired
    ConfigurationRepository configurationRepository;
    @Autowired
    ObjectMapper objectMapper;


    public List<UserDefinedOfflinePaymentMethod> getOrganizationCustomOfflinePaymentMethods(int orgId) throws JsonProcessingException {
        var config = configurationRepository
            .findByKeyAtOrganizationLevel(orgId, ConfigurationKeys.CUSTOM_OFFLINE_PAYMENTS.getValue())
            .map(cf -> cf.getValue())
            .orElse(null);

        List<UserDefinedOfflinePaymentMethod> paymentMethods = List.of();
        if(config != null) {
            paymentMethods = objectMapper.readValue(
                config,
                new TypeReference<List<UserDefinedOfflinePaymentMethod>>(){}
            );
        }

        return paymentMethods;
    }

    public List<UserDefinedOfflinePaymentMethod> getAllowedCustomOfflinePaymentMethodsForEvent(Event event) throws JsonProcessingException {
        var paymentMethods = getOrganizationCustomOfflinePaymentMethods(event.getOrganizationId());

        var allowedMethodIDsForEvent = configurationManager
            .getFor(ConfigurationKeys.SELECTED_CUSTOM_PAYMENTS, ConfigurationLevel.event(event))
            .getValue()
            .map(v -> Json.fromJson(v, new TypeReference<List<String>>() {}))
            .orElse(new ArrayList<>());

        var allowedPaymentMethods = paymentMethods
            .stream()
            .filter(pm -> allowedMethodIDsForEvent.contains(pm.getPaymentMethodId()))
            .toList();

        return allowedPaymentMethods;
    }
}
