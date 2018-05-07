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

import alfio.controller.decorator.SaleableTicketCategory;
import alfio.manager.EventManager;
import alfio.manager.TicketReservationManager;
import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.SpecialPrice;
import alfio.model.TicketCategory;
import alfio.model.modification.ASReservationWithOptionalCodeModification;
import alfio.model.modification.AdditionalServiceReservationModification;
import alfio.model.modification.TicketReservationModification;
import alfio.model.modification.TicketReservationWithOptionalCodeModification;
import alfio.repository.AdditionalServiceRepository;
import alfio.util.ErrorsCode;
import alfio.util.OptionalWrapper;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.validation.Errors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

//step 1 : choose tickets
@Data
public class ReservationForm implements Serializable {

    private String promoCode;
    private List<TicketReservationModification> reservation;
    private List<AdditionalServiceReservationModification> additionalService;

    public List<TicketReservationModification> selected() {
        return ofNullable(reservation)
                .orElse(emptyList())
                .stream()
                .filter((e) -> e != null && e.getAmount() != null && e.getTicketCategoryId() != null
                        && e.getAmount() > 0).collect(toList());
    }

    public List<AdditionalServiceReservationModification> selectedAdditionalServices() {
        return ofNullable(additionalService)
            .orElse(emptyList())
            .stream()
            .filter(e -> e != null && e.getQuantity() != null && e.getAdditionalServiceId() != null && e.getQuantity() > 0)
            .collect(toList());
    }

    public int ticketSelectionCount() {
        return selected().stream().mapToInt(TicketReservationModification::getAmount).sum();
    }

    private int additionalServicesSelectionCount(AdditionalServiceRepository additionalServiceRepository, int eventId) {
        return (int) selectedAdditionalServices().stream()
            .filter(as -> as.getAdditionalServiceId() != null && (additionalServiceRepository.getById(as.getAdditionalServiceId(), eventId).isFixPrice() || Optional.ofNullable(as.getAmount()).filter(a -> a.compareTo(BigDecimal.ZERO) > 0).isPresent()))
            .count();
    }
}
