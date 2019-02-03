package alfio.model.transaction.webhook;

import alfio.model.transaction.TransactionWebhookPayload;
import com.stripe.model.PaymentIntent;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class StripePaymentIntentWebhookPayload implements TransactionWebhookPayload {

    private final String type;
    private final PaymentIntent payload;

    @Override
    public PaymentIntent getPayload() {
        return payload;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getReservationId() {
        return payload.getMetadata().get("reservationId");
    }

    @Override
    public Status getStatus() {
        return payload.getStatus().equals("succeeded") ? Status.SUCCESS : Status.FAILURE;
    }
}
