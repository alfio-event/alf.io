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
package alfio.model;

import alfio.model.modification.EventWithStatistics;
import alfio.model.modification.StatisticsContainer;
import alfio.model.transaction.PaymentProxy;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;


public class EventStatistic implements StatisticsContainer {


    @JsonIgnore
    private final EventWithStatistics eventWithStatistics;

    public EventStatistic(EventWithStatistics eventWithStatistics) {
        this.eventWithStatistics = eventWithStatistics;
    }

    public List<PaymentProxy> getAllowedPaymentProxies() {
        return eventWithStatistics.getAllowedPaymentProxies();
    }

    public boolean isWarningNeeded() {
        return eventWithStatistics.isWarningNeeded();
    }

    public int getAvailableSeats() {
        return eventWithStatistics.getAvailableSeats();
    }

    public String getFormattedBegin() {
        return eventWithStatistics.getFormattedBegin();
    }

    public String getFormattedEnd() {
        return eventWithStatistics.getFormattedEnd();
    }

    public boolean isExpired() {
        return eventWithStatistics.isExpired();
    }

    public String getShortName() {
        return eventWithStatistics.getShortName();
    }

    public String getDisplayName() {
        return eventWithStatistics.getDisplayName();
    }

    @Override
    public int getNotSoldTickets() {
        return eventWithStatistics.getNotSoldTickets();
    }

    @Override
    public int getSoldTickets() {
        return eventWithStatistics.getSoldTickets();
    }

    @Override
    public int getCheckedInTickets() {
        return eventWithStatistics.getCheckedInTickets();
    }

    @Override
    public int getNotAllocatedTickets() {
        return eventWithStatistics.getNotAllocatedTickets();
    }

    @Override
    public int getPendingTickets() {
        return eventWithStatistics.getPendingTickets();
    }

    @Override
    public int getDynamicAllocation() {
        return eventWithStatistics.getDynamicAllocation();
    }

    public int getOrganizationId() {
        return eventWithStatistics.getOrganizationId();
    }

    public int getId() {
        return eventWithStatistics.getId();
    }
}
