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
package alfio.manager;

import alfio.model.ReservationLink;
import alfio.repository.ReservationLinkRepository;
import alfio.util.ClockProvider;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
public class ReservationLinkManager {

    private final ReservationLinkRepository reservationLinkRepository;
    private final ClockProvider clockProvider;

    public ReservationLink generate(int eventId, int categoryId, int quantity, ZonedDateTime expiresAt) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be >= 1");
        }
        if (!expiresAt.isAfter(ZonedDateTime.now(clockProvider.getClock()))) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        String token = UUID.randomUUID().toString();
        reservationLinkRepository.create(token, eventId, categoryId, quantity, expiresAt);
        return reservationLinkRepository.findByToken(token).orElseThrow();
    }

    public void revoke(int id) {
        reservationLinkRepository.markAsRevoked(id);
    }

    public Optional<ReservationLink> attemptUse(String token) {
        return reservationLinkRepository.findByToken(token).flatMap(link -> {
            if (link.getStatus() != ReservationLink.Status.ACTIVE) {
                return Optional.empty();
            }
            ZonedDateTime now = ZonedDateTime.now(clockProvider.getClock());
            if (!link.getExpiresAt().isAfter(now)) {
                return Optional.empty();
            }
            int updated = reservationLinkRepository.markAsUsed(link.getId(), now);
            return updated == 1 ? Optional.of(link) : Optional.empty();
        });
    }

    public List<ReservationLink> findByCategoryId(int categoryId) {
        return reservationLinkRepository.findByCategoryId(categoryId);
    }
}
