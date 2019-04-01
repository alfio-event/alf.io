package alfio.model.transaction.webhook;

import alfio.model.transaction.TransactionWebhookPayload;
import alfio.model.transaction.provider.RevolutTransactionDescriptor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

public class RevolutWebhookPayload implements TransactionWebhookPayload {

    private final String type;
    private final ZonedDateTime timestamp;
    private final RevolutTransactionDescriptor payload;
    private String reservationId;

    @JsonCreator
    public RevolutWebhookPayload(@JsonProperty("event") String type,
                                 @JsonProperty("timestamp") ZonedDateTime timestamp,
                                 @JsonProperty("data") RevolutTransactionDescriptor payload) {
        this.type = type;
        this.timestamp = timestamp;
        this.payload = payload;
    }


    @Override
    public RevolutTransactionDescriptor getPayload() {
        return payload;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getReservationId() {
        return reservationId;
    }

    @Override
    public Status getStatus() {
        return payload.getState().equals("completed") ? Status.SUCCESS : Status.FAILURE;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public RevolutWebhookPayload attachToReservation(String reservationId) {
        this.reservationId = reservationId;
        return this;
    }
}