package alfio.model.transaction.webhook;

import alfio.model.transaction.TransactionWebhookPayload;

public class MollieWebhookPayload implements TransactionWebhookPayload {

    private final String paymentId;
    private final String eventName;

    public MollieWebhookPayload(String paymentId, String eventName) {
        this.paymentId = paymentId;
        this.eventName = eventName;
    }

    @Override
    public String getPayload() {
        return paymentId;
    }

    @Override
    public String getType() {
        return null;
    }

    @Override
    public String getReservationId() {
        return null;
    }

    @Override
    public Status getStatus() {
        return null;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getEventName() {
        return eventName;
    }
}
