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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Objects;
import java.util.Optional;

public final class PaymentResult {

    public enum Type { SUCCESSFUL, INITIALIZED, PENDING, REDIRECT, FAILED }

    private final Type type;
    @JsonIgnore
    private Optional<String> gatewayId = Optional.empty();
    @JsonIgnore
    private Optional<String> errorCode = Optional.empty();
    private String redirectUrl;

    private PaymentResult(Type type) {
        this.type = type;
    }

    public boolean isSuccessful() {
        return type == Type.SUCCESSFUL;
    }

    public boolean isFailed() { return type == Type.FAILED; }

    public boolean isRedirect() {
        return type == Type.REDIRECT;
    }

    public boolean isInitialized() {
        return type == Type.INITIALIZED;
    }

    public Optional<String> getErrorCode() {
        return errorCode;
    }

    private PaymentResult setErrorCode( String errorCode ) {
        this.errorCode = Optional.ofNullable(errorCode);
        return this;
    }

    public Optional<String> getGatewayId() {
        return gatewayId;
    }

    public String getGatewayIdOrNull() {
        return gatewayId.orElse(null);
    }

    private PaymentResult setGatewayId(String gatewayId) {
        this.gatewayId = Optional.ofNullable(gatewayId);
        return this;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    private PaymentResult setRedirectUrl( String redirectUrl ) {
        this.redirectUrl = redirectUrl;
        return this;
    }

    public static PaymentResult successful( String gatewayId) {
        return new PaymentResult(Type.SUCCESSFUL).setGatewayId( gatewayId );
    }

    public static PaymentResult redirect(String redirectUrl) {
        return new PaymentResult(Type.REDIRECT).setRedirectUrl( redirectUrl );
    }

    public static PaymentResult pending(String gatewayId) {
        return new PaymentResult(Type.PENDING).setGatewayId( gatewayId );
    }

    public static PaymentResult initialized(String gatewayId) {
        return new PaymentResult(Type.INITIALIZED).setGatewayId( gatewayId );
    }

    public static PaymentResult failed( String errorCode) {
        return new PaymentResult(Type.FAILED).setErrorCode( errorCode );
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("type", type)
            .append("gatewayId", gatewayId)
            .append("errorCode", errorCode)
            .append("redirectUrl", redirectUrl)
            .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentResult that = (PaymentResult) o;
        return type == that.type && Objects.equals(gatewayId, that.gatewayId) && Objects.equals(errorCode, that.errorCode) && Objects.equals(redirectUrl, that.redirectUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, gatewayId, errorCode, redirectUrl);
    }
}
