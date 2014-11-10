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

import lombok.EqualsAndHashCode;

import java.util.Optional;

@EqualsAndHashCode
public final class PaymentResult {

    private final boolean successful;
    private final Optional<String> gatewayTransactionId;
    private final Optional<String> errorCode;

    private PaymentResult(boolean successful, Optional<String> gatewayTransactionId, Optional<String> errorCode) {
        this.successful = successful;
        this.gatewayTransactionId = gatewayTransactionId;
        this.errorCode = errorCode;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public Optional<String> getErrorCode() {
        return errorCode;
    }

    public Optional<String> getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public static PaymentResult successful(String gatewayTransactionId) {
        return new PaymentResult(true, Optional.of(gatewayTransactionId), Optional.empty());
    }

    public static PaymentResult unsuccessful(String errorCode) {
        return new PaymentResult(false, Optional.empty(), Optional.of(errorCode));
    }
}
