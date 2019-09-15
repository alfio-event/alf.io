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

import alfio.model.TicketWithCategory;

import java.util.List;

import static alfio.manager.support.CheckInStatus.SUCCESS;

public class SuccessfulCheckIn extends TicketAndCheckInResult {
    private final List<AdditionalServiceInfo> additionalServices;
    private final String boxColor;

    public SuccessfulCheckIn(TicketWithCategory ticket,
                             List<AdditionalServiceInfo> additionalServices,
                             String boxColor) {
        super(ticket, new DefaultCheckInResult(SUCCESS, "success"));
        this.additionalServices = additionalServices;
        this.boxColor = boxColor;
    }

    public List<AdditionalServiceInfo> getAdditionalServices() {
        return additionalServices;
    }

    public String getBoxColor() {
        return boxColor;
    }
}
