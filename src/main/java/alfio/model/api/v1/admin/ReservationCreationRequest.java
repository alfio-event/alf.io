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
package alfio.model.api.v1.admin;

import alfio.controller.form.ReservationCreate;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationModification;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ReservationCreationRequest implements ReservationCreate {

    private final List<TicketReservationModification> tickets;
    private final List<AdditionalServiceReservationModification> additionalServices;
    private final ReservationUser user;
    private final String promoCode;
    private final String language;

    @JsonCreator
    public ReservationCreationRequest(@JsonProperty("tickets") List<TicketReservationModification> tickets,
                                      @JsonProperty("additionalServices") List<AdditionalServiceReservationModification> additionalServices,
                                      @JsonProperty("user") ReservationUser user,
                                      @JsonProperty("promoCode") String promoCode,
                                      @JsonProperty("language") String language) {
        this.tickets = tickets;
        this.additionalServices = additionalServices;
        this.user = user;
        this.promoCode = promoCode;
        this.language = language;
    }


    @Override
    public String getPromoCode() {
        return promoCode;
    }

    @Override
    public List<TicketReservationModification> getTickets() {
        return tickets;
    }

    @Override
    public List<AdditionalServiceReservationModification> getAdditionalServices() {
        return additionalServices;
    }

    @Override
    public String getCaptcha() {
        return null;
    }

    public String getLanguage() {
        return language;
    }

    public ReservationUser getUser() {
        return user;
    }
}
