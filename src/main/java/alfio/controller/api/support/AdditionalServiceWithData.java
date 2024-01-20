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
package alfio.controller.api.support;

import alfio.model.AdditionalService;

import java.util.List;
import java.util.Map;


public class AdditionalServiceWithData {
    private final Map<String, String> title;
    private final int itemId;
    private final int serviceId;
    private final String ticketUUID;
    private final List<AdditionalField> ticketFieldConfiguration;

    private final AdditionalService.AdditionalServiceType type;

    public AdditionalServiceWithData(Map<String, String> title,
                                     int itemId,
                                     Integer serviceId,
                                     String ticketUUID,
                                     List<AdditionalField> ticketFieldConfiguration,
                                     AdditionalService.AdditionalServiceType type) {
        this.title = title;
        this.itemId = itemId;
        this.serviceId = serviceId;
        this.ticketUUID = ticketUUID;
        this.ticketFieldConfiguration = ticketFieldConfiguration;
        this.type = type;
    }

    public Map<String, String> getTitle() {
        return title;
    }

    public int getItemId() {
        return itemId;
    }

    public int getServiceId() {
        return serviceId;
    }

    public String getTicketUUID() {
        return ticketUUID;
    }

    public List<AdditionalField> getTicketFieldConfiguration() {
        return ticketFieldConfiguration;
    }

    public AdditionalService.AdditionalServiceType getType() {
        return type;
    }
}
