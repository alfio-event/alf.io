package alfio.model.transaction.provider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Getter
public class RevolutTransactionDescriptor {

    private final String id;
    private final String type;
    private final String requestId;
    private final String state;
    private final ZonedDateTime createdAt;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime completedAt;
    private final String reference;
    private final List<TransactionLeg> legs;

    @JsonCreator
    public RevolutTransactionDescriptor(@JsonProperty("id") String id,
                                        @JsonProperty("type") String type,
                                        @JsonProperty("request_id") String requestId,
                                        @JsonProperty("state") String state,
                                        @JsonProperty("created_at") ZonedDateTime createdAt,
                                        @JsonProperty("updated_at") ZonedDateTime updatedAt,
                                        @JsonProperty("completed_at") ZonedDateTime completedAt,
                                        @JsonProperty("reference") String reference,
                                        @JsonProperty("legs") List<TransactionLeg> legs) {
        this.id = id;
        this.type = type;
        this.requestId = requestId;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.completedAt = completedAt;
        this.reference = reference;
        this.legs = legs;
    }

    @Getter
    public static class TransactionLeg {
        private final String id;
        private final String accountId;
        private final String counterpartyAccountId;
        private final String counterpartyType;
        private final BigDecimal amount;
        private final String currency;
        private final BigDecimal balance;

        @JsonCreator
        public TransactionLeg(@JsonProperty("leg_id") String id,
                              @JsonProperty("account_id") String accountId,
                              @JsonProperty("counterparty") Counterparty counterparty,
                              @JsonProperty("amount") BigDecimal amount,
                              @JsonProperty("currency") String currency,
                              @JsonProperty("balance") BigDecimal balance) {
            this.id = id;
            this.accountId = accountId;
            this.counterpartyAccountId = counterparty.id;
            this.counterpartyType = counterparty.type;
            this.amount = amount;
            this.currency = currency;
            this.balance = balance;
        }
    }

    public BigDecimal getTransactionBalance() {
        return legs.stream().map(TransactionLeg::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Map<String, String> getMetadata() {
        return Map.of(
            "counterpartyAccountId", legs.get(0).counterpartyAccountId,
            "counterpartyType", legs.get(0).counterpartyType
        );
    }

    private static class Counterparty {
        private final String type;
        private final String id;

        @JsonCreator
        public Counterparty(@JsonProperty("type") String type,
                            @JsonProperty("account_id") String id) {
            this.type = type;
            this.id = id;
        }
    }
}


