package alfio.controller.support;

import alfio.model.Ticket;
import lombok.experimental.Delegate;

import java.util.List;
import java.util.stream.Collectors;

public class TicketDecorator {

    @Delegate
    private final Ticket ticket;
    private final boolean freeCancellationEnabled;

    public TicketDecorator(Ticket ticket, boolean freeCancellationEnabled) {
        this.ticket = ticket;
        this.freeCancellationEnabled = freeCancellationEnabled;
    }

    public boolean hasBeenPaid() {
        return !isFree();
    }

    public boolean isFree() {
        return getPaidPriceInCents() == 0;
    }

    public boolean getCancellationEnabled() {
        return isFree() && freeCancellationEnabled;
    }

    public static List<TicketDecorator> decorate(List<Ticket> tickets, boolean freeCancellationEnabled) {
        return tickets.stream().map(t -> new TicketDecorator(t, freeCancellationEnabled)).collect(Collectors.toList());
    }
}
