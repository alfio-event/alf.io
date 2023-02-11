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

import alfio.model.ReservationsByEvent;
import alfio.repository.ExportRepository;
import alfio.util.ClockProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ExportManager {

    private final ExportRepository exportRepository;
    private final ClockProvider clockProvider;

    public ExportManager(ExportRepository exportRepository,
                         ClockProvider clockProvider) {
        this.exportRepository = exportRepository;
        this.clockProvider = clockProvider;
    }

    public List<ReservationsByEvent> reservationsForInterval(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Wrong interval");
        }
        var zoneId = clockProvider.getClock().getZone();
        var zonedFrom = from.atStartOfDay().atZone(zoneId);
        var zonedTo = to.plusDays(1).atStartOfDay().minusSeconds(1).atZone(zoneId);
        return exportRepository.allReservationsForInterval(zonedFrom, zonedTo);
    }
}
