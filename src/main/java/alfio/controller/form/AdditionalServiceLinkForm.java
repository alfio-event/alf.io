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
package alfio.controller.form;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdditionalServiceLinkForm implements Serializable, AdditionalFieldsContainer {

    private Integer additionalServiceItemId;
    private String ticketUUID;
    private Map<String, List<String>> additional = new HashMap<>();


    public boolean isValid() {
        return additional != null && additionalServiceItemId != null && ticketUUID != null;
    }

    public Integer getAdditionalServiceItemId() {
        return additionalServiceItemId;
    }

    public void setAdditionalServiceItemId(Integer additionalServiceItemId) {
        this.additionalServiceItemId = additionalServiceItemId;
    }

    public String getTicketUUID() {
        return ticketUUID;
    }

    public void setTicketUUID(String ticketUUID) {
        this.ticketUUID = ticketUUID;
    }

    @Override
    public Map<String, List<String>> getAdditional() {
        return additional;
    }

    @Override
    public void setAdditional(Map<String, List<String>> additionalFields) {
        this.additional = additionalFields;
    }
}
