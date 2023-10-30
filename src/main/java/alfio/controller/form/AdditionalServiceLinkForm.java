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

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

public class AdditionalServiceLinkForm {

    private List<AdditionalServiceLink> additionalServiceLinks;

    public List<AdditionalServiceLink> getAdditionalServiceLinks() {
        return additionalServiceLinks;
    }

    public void setAdditionalServiceLinks(List<AdditionalServiceLink> additionalServiceLinks) {
        this.additionalServiceLinks = additionalServiceLinks;
    }

    public boolean isValid() {
        return CollectionUtils.isEmpty(additionalServiceLinks) || additionalServiceLinks.stream().allMatch(AdditionalServiceLink::isValid);
    }

    public static class AdditionalServiceLink {
        private int additionalServiceItemId;
        private String ticketUUID;

        public int getAdditionalServiceItemId() {
            return additionalServiceItemId;
        }

        public void setAdditionalServiceItemId(int additionalServiceItemId) {
            this.additionalServiceItemId = additionalServiceItemId;
        }

        public String getTicketUUID() {
            return ticketUUID;
        }

        public void setTicketUUID(String ticketUUID) {
            this.ticketUUID = ticketUUID;
        }

        public boolean isValid() {
            return this.ticketUUID != null;
        }
    }
}
