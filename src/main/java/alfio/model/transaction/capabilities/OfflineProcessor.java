package alfio.model.transaction.capabilities;

import alfio.model.TicketReservationWithTransaction;
import alfio.model.result.Result;
import alfio.model.transaction.Capability;
import alfio.model.transaction.PaymentContext;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public interface OfflineProcessor extends Capability {
    /**
     * Verify if the given reservations have been paid according to the payment provider.
     *
     * @param reservations the reservations to check
     * @param paymentContext
     * @param lastCheck last check timestamp, can be null. Defaults to <code>now - 24h</code>
     * @return a list of {@link TicketReservationWithTransaction}s.
     */
    Result<List<TicketReservationWithTransaction>> checkPendingReservations(Collection<TicketReservationWithTransaction> reservations,
                                                                            PaymentContext paymentContext,
                                                                            ZonedDateTime lastCheck);

}
