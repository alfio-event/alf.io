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

import alfio.model.modification.StatisticsContainer;
import alfio.model.transaction.PaymentProxy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;


public class EventStatistic implements StatisticsContainer, Comparable<EventStatistic> {

    public static final DateTimeFormatter JSON_DATE_FORMATTER = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm");


    @JsonIgnore
    private final Event event;

    @JsonIgnore
    private final EventStatisticView eventStatisticView;
    @JsonIgnore
    private final boolean statisticsEnabled;

    public EventStatistic(Event event, EventStatisticView eventStatisticView, boolean statisticsEnabled) {
        this.event = event;
        this.eventStatisticView = eventStatisticView;
        this.statisticsEnabled = statisticsEnabled;
    }

    public List<PaymentProxy> getAllowedPaymentProxies() {
        return event.getAllowedPaymentProxies();
    }

    public boolean isWarningNeeded() {
        return !isExpired() && (eventStatisticView.isContainsOrphanTickets() || eventStatisticView.isContainsStuckReservations());
    }

    public int getAvailableSeats() {
        return eventStatisticView.getAvailableSeats();
    }

    public String getFormattedBegin() {
        return event.getBegin().format(JSON_DATE_FORMATTER);
    }

    public String getFormattedEnd() {
        return event.getEnd().format(JSON_DATE_FORMATTER);
    }

    public boolean isExpired() {
        return event.expired();
    }

    public String getShortName() {
        return event.getShortName();
    }

    public String getDisplayName() {
        return event.getDisplayName();
    }

    @Override
    public int getNotSoldTickets() {
        return eventStatisticView.getNotSoldTickets();
    }

    @Override
    public int getSoldTickets() {
        return eventStatisticView.getSoldTickets();
    }

    @Override
    public int getCheckedInTickets() {
        return eventStatisticView.getCheckedInTickets();
    }

    @Override
    public int getNotAllocatedTickets() {
        return eventStatisticView.getNotAllocatedTickets();
    }

    @Override
    public int getPendingTickets() {
        return eventStatisticView.getPendingTickets();
    }

    @Override
    public int getDynamicAllocation() {
        return eventStatisticView.getDynamicAllocation();
    }

    @Override
    public int getReleasedTickets() {
        return eventStatisticView.getReleasedTickets();
    }

    public int getOrganizationId() {
        return event.getOrganizationId();
    }

    public int getId() {
        return event.getId();
    }

    public Event.Status getStatus() {
        return event.getStatus();
    }

    public String getFileBlobId() {
        return event.getFileBlobId();
    }

    public boolean isVisibleForCurrentUser() { return eventStatisticView.isLiveData(); }

    public boolean isDisplayStatistics() {
        return isVisibleForCurrentUser() && statisticsEnabled;
    }

    @Override
    public int compareTo(EventStatistic o) {
        CompareToBuilder builder = new CompareToBuilder();
        return builder.append(isExpired(), o.isExpired()).append(event.getBegin().withZoneSameInstant(ZoneId.systemDefault()), o.event.getBegin().withZoneSameInstant(ZoneId.systemDefault())).build();
    }
}
