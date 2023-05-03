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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Builder
@Getter
public class EventTicketClass implements WalletEntity {

    public static final String WALLET_URL = "https://walletobjects.googleapis.com/walletobjects/v1/eventTicketClass";

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSX");

    private String id;

    private String eventName;

    private String eventOrGroupingId;

    private String description;

    private String venue;

    private LatitudeLongitudePoint location;

    private String ticketType;

    private String logoUri;

    private ZonedDateTime start;

    private ZonedDateTime end;

    public String build(ObjectMapper mapper) {
        ObjectNode object = mapper.createObjectNode();
        object.put("id", id);
        object.put("eventId", eventOrGroupingId);
        object.put("multipleDevicesAndHoldersAllowedStatus", "ONE_USER_ALL_DEVICES");
        object.put("issuerName", eventName);
        object.set("eventName",
            mapper.createObjectNode().set("defaultValue", mapper.createObjectNode()
                .put("language", "en-US")
                .put("value", ticketType)));
        object.set("venue",
            mapper.createObjectNode().setAll(
                Map.of(
                    "name", mapper.createObjectNode().set("defaultValue", mapper.createObjectNode()
                        .put("language", "en-US")
                        .put("value", venue)),
                    "address", mapper.createObjectNode().set("defaultValue", mapper.createObjectNode()
                        .put("language", "en-US")
                        .put("value", venue))
                )));
        object.set("dateTime",
            mapper.createObjectNode()
                .put("start", start.format(EVENT_TIME_FORMATTER))
                .put("end", end.format(EVENT_TIME_FORMATTER)));
        object.put("reviewStatus", "UNDER_REVIEW");
        object.put("hexBackgroundColor", "#FFFFFF");
        object.set("logo",
            mapper.createObjectNode().set("sourceUri", mapper.createObjectNode()
                .put("uri", logoUri)));
        object.set("classTemplateInfo",
            mapper.createObjectNode().set("cardTemplateOverride",
                mapper.createObjectNode().set("cardRowTemplateInfos",
                    mapper.createArrayNode()
                        .add(mapper.createObjectNode().set("oneItem",
                            mapper.createObjectNode().setAll(
                                Map.of(
                                    "item", mapper.createObjectNode().set("firstValue",
                                        mapper.createObjectNode().set("fields",
                                            mapper.createArrayNode()
                                                .add(mapper.createObjectNode().put("fieldPath", "class.dateTime.start"))))))))
                        .add(mapper.createObjectNode().set("oneItem",
                            mapper.createObjectNode().setAll(
                                Map.of(
                                    "item", mapper.createObjectNode().set("firstValue",
                                        mapper.createObjectNode().set("fields",
                                            mapper.createArrayNode()
                                                .add(mapper.createObjectNode().put("fieldPath", "class.dateTime.end"))))))))
                )));
        object.set("linksModuleData",
            mapper.createObjectNode().set("uris",
                mapper.createArrayNode()
                    .add(mapper.createObjectNode()
                        .put("uri", "https://alf.io")
                        .put("description", "Powered by Alf.io, the Open Source ticket reservation system.")
                        .put("id", "alfio")))
        );
        object.set("messages", mapper.createArrayNode()
            .add(mapper.createObjectNode()
                .put("header", "Event Description")
                .put("body", description))
        );
        if (location != null) {
            object.set("locations", location.accept(mapper));
        }

        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Getter
    public static class LatitudeLongitudePoint {
        private final Double latitude;

        private final Double longitude;

        private LatitudeLongitudePoint(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public static LatitudeLongitudePoint of(Double latitude, Double longitude) {
            return new LatitudeLongitudePoint(latitude, longitude);
        }

        private ArrayNode accept(ObjectMapper mapper) {
            return mapper.createArrayNode()
                .add(mapper.createObjectNode()
                    .put("latitude", latitude)
                    .put("longitude", longitude)
                );
        }
    }
}
