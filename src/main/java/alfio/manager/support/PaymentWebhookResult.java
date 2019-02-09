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
package alfio.manager.support;

import alfio.model.transaction.PaymentToken;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PaymentWebhookResult {

    public enum Type {
        NOT_RELEVANT,
        REJECTED,
        SUCCESSFUL,
        FAILED
    }

    private final Type type;
    private final PaymentToken paymentToken;
    private final String reason;

    public boolean isSuccessful() {
        return type == Type.SUCCESSFUL;
    }

    public boolean isFailed() {
        return type == Type.FAILED;
    }

    public static PaymentWebhookResult successful(PaymentToken paymentToken) {
        return new PaymentWebhookResult(Type.SUCCESSFUL, paymentToken, null);
    }

    public static PaymentWebhookResult failed(String reason) {
        return new PaymentWebhookResult(Type.FAILED, null, reason);
    }

    public static PaymentWebhookResult notRelevant(String reason) {
        return new PaymentWebhookResult(Type.NOT_RELEVANT, null, reason);
    }
}
