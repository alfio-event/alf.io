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
        TRANSACTION_INITIATED,
        SUCCESSFUL,
        FAILED,
        CANCELLED,
        ERROR
    }

    private final Type type;
    private final PaymentToken paymentToken;
    private final String reason;
    private final String redirectUrl;

    public boolean isSuccessful() {
        return type == Type.SUCCESSFUL;
    }

    public boolean isError() {
        return type == Type.ERROR;
    }

    public static PaymentWebhookResult successful(PaymentToken paymentToken) {
        return new PaymentWebhookResult(Type.SUCCESSFUL, paymentToken, null, null);
    }

    public static PaymentWebhookResult failed(String reason) {
        return new PaymentWebhookResult(Type.FAILED, null, reason, null);
    }

    public static PaymentWebhookResult cancelled() {
        return new PaymentWebhookResult(Type.CANCELLED, null, null, null);
    }

    public static PaymentWebhookResult error(String reason) {
        return new PaymentWebhookResult(Type.ERROR, null, reason, null);
    }

    public static PaymentWebhookResult notRelevant(String reason) {
        return new PaymentWebhookResult(Type.NOT_RELEVANT, null, reason, null);
    }

    public static PaymentWebhookResult processStarted(PaymentToken paymentToken) {
        return new PaymentWebhookResult(Type.TRANSACTION_INITIATED, paymentToken, null, null);
    }

    public static PaymentWebhookResult pending() {
        return new PaymentWebhookResult(Type.TRANSACTION_INITIATED, null, null, null);
    }

    public static PaymentWebhookResult redirect(String redirectUrl) {
        return new PaymentWebhookResult(Type.TRANSACTION_INITIATED, null, null, redirectUrl);
    }
}
