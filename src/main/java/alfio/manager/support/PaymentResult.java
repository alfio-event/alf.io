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

import java.util.Optional;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public final class PaymentResult {

    public enum Type { SUCCESSFUL, PENDING, REDIRECT, FAILED };

    private final Type type;
    private Optional<String> gatewayTransactionId = Optional.empty();
    private Optional<String> errorCode = Optional.empty();
    private String redirectUrl;

    private PaymentResult(Type type) {
        this.type = type;
    }

    public boolean isSuccessful() {
        return type == Type.SUCCESSFUL;
    }

    public boolean isRedirect() {
        return type == Type.REDIRECT;
    }

    public Optional<String> getErrorCode() {
        return errorCode;
    }

    private PaymentResult setErrorCode( String errorCode ) {
        this.errorCode = Optional.of(errorCode);
        return this;
    }

    public Optional<String> getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    private PaymentResult setGatewayTransactionId( String gatewayTransactionId ) {
        this.gatewayTransactionId = Optional.of(gatewayTransactionId);
        return this;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    private PaymentResult setRedirectUrl( String redirectUrl ) {
        this.redirectUrl = redirectUrl;
        return this;
    }

    public static PaymentResult successful( String gatewayTransactionId) {
        return new PaymentResult(Type.SUCCESSFUL).setGatewayTransactionId( gatewayTransactionId );
    }

    public static PaymentResult redirect(String redirectUrl) {
        return new PaymentResult(Type.REDIRECT).setRedirectUrl( redirectUrl );
    }

    public static PaymentResult pending(String gatewayTransactionId) {
        return new PaymentResult(Type.PENDING).setGatewayTransactionId( gatewayTransactionId );
    }

    public static PaymentResult failed( String errorCode) {
        return new PaymentResult(Type.FAILED).setErrorCode( errorCode );
    }
}
