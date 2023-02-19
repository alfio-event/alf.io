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

import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationModification;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

//step 1 : choose tickets
@Data
public class ReservationForm implements Serializable, ReservationCreate<TicketReservationModification> {

    private String promoCode;
    private List<TicketReservationModification> reservation;
    private List<AdditionalServiceReservationModification> additionalService;
    private String captcha;

    @Override
    public List<TicketReservationModification> getTickets() {
        return reservation;
    }

    @Override
    public List<AdditionalServiceReservationModification> getAdditionalServices() {
        return additionalService;
    }
}
