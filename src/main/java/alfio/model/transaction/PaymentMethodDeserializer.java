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
package alfio.model.transaction;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class PaymentMethodDeserializer extends JsonDeserializer<PaymentMethod> {
    private static final Logger log = LoggerFactory.getLogger(PaymentMethodDeserializer.class);

    @Override
    public PaymentMethod deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        if (node == null) {
            log.warn("PaymentMethodDeserializer(): Passed node is NULL");
            return null;
        }

        if(node.isTextual()) {
            String name = node.asText();
            for(StaticPaymentMethods paymentMethod : StaticPaymentMethods.values()) {
                if(paymentMethod.name().equals(name)) {
                    return paymentMethod;
                }
            }
        }

        if(node.isObject()) {
            String paymentMethodId = node.get("paymentMethodId").isNull() ? null
                                    : node.get("paymentMethodId").asText();

            JsonNode localizationsNode = node.get("localizations");
            ObjectMapper mapper = (ObjectMapper) parser.getCodec();

            var localizations = mapper.readValue(
                localizationsNode.toString(),
                new TypeReference<Map<String, UserDefinedOfflinePaymentMethod.Localization>>() {}
            );

            var paymentMethod = new UserDefinedOfflinePaymentMethod(paymentMethodId, localizations);

            JsonNode deletedNode = node.get("deleted");
            if(deletedNode != null && deletedNode.asBoolean()) {
                paymentMethod.setDeleted();
            }

            return paymentMethod;
        }

        return null;
    }
}
