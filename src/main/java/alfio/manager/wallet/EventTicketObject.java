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
package alfio.manager.wallet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.core.io.ByteArrayResource;

import java.net.URI;
import java.time.format.DateTimeFormatter;

@Builder
@Getter
public class EventTicketObject implements WalletEntity {

    public static final String WALLET_URL = "https://walletobjects.googleapis.com/walletobjects/v1/eventTicketObject";

    private String id;

    private String classId;

    private String ticketHolderName;

    private String ticketNumber;

    private String barcode;

    public String build(ObjectMapper mapper) {
        ObjectNode object = mapper.createObjectNode();
        object.put("id", id);
        object.put("classId", classId);
        object.put("state", "ACTIVE");
        object.put("ticketHolderName", ticketHolderName);
        object.put("ticketNumber", ticketNumber);
        object.set("barcode", mapper.createObjectNode()
            .put("type", "QR_CODE")
            .put("value", barcode));

        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
