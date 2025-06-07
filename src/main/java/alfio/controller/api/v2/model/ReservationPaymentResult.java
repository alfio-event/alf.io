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
package alfio.controller.api.v2.model;

public class ReservationPaymentResult {
    private final boolean success;
    private final boolean redirect;
    private final String redirectUrl;
    private final boolean failure;
    private final String gatewayIdOrNull;

    public ReservationPaymentResult(boolean success, boolean redirect, String redirectUrl, boolean failure, String gatewayIdOrNull) {
        this.success = success;
        this.redirect = redirect;
        this.redirectUrl = redirectUrl;
        this.failure = failure;
        this.gatewayIdOrNull = gatewayIdOrNull;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isRedirect() {
        return redirect;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public boolean isFailure() {
        return failure;
    }

    public String getGatewayIdOrNull() {
        return gatewayIdOrNull;
    }
}
