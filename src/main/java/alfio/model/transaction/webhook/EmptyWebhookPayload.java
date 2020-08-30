package alfio.model.transaction.webhook;

import alfio.model.transaction.TransactionWebhookPayload;
import lombok.AllArgsConstructor;

/**
 * Special {@link alfio.model.transaction.TransactionWebhookPayload} for providers that don't send a payload
 * along with the Webhook
 */
@AllArgsConstructor
public class EmptyWebhookPayload implements TransactionWebhookPayload {

    private final String reservationId;
    private final Status status;

    @Override
    public String getPayload() {
        return "empty";
    }

    @Override
    public String getType() {
        return "empty";
    }

    @Override
    public String getReservationId() {
        return reservationId;
    }

    @Override
    public Status getStatus() {
        return status;
    }

}
