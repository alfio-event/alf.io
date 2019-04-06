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
package alfio.model.transaction.provider;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            "counterpartyAccountId", Optional.ofNullable(legs.get(0).counterpartyAccountId).orElse("N/A"),
            "counterpartyType", Optional.ofNullable(legs.get(0).counterpartyType).orElse("N/A")
        );
    }

    private static class Counterparty {
        private final String type;
        private final String id;

        @JsonCreator
        public Counterparty(@JsonProperty("account_type") String type,
                            @JsonProperty("account_id") String id) {
            this.type = type;
            this.id = id;
        }
    }
}


