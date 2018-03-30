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
package alfio.model.transaction;

public class PaymentConfiguration {

    public enum WebHook { NONE, POST_PAYMENT, BOTH }

    private boolean synchronous;
    private boolean requiresRedirect;
    private final WebHook webHook;

    private final PaymentMethod paymentMethod;

    public PaymentConfiguration( PaymentMethod paymentMethod, WebHook webHook ) {
        this.paymentMethod = paymentMethod;
        this.webHook = webHook;
    }

    public PaymentConfiguration synchronous() {
        return synchronous(true);
    }

    public PaymentConfiguration synchronous(boolean synchronous) {
        this.synchronous = synchronous;
        return this;
    }

    public PaymentConfiguration requiresRedirect() {
        return requiresRedirect(true);
    }

    public PaymentConfiguration requiresRedirect(boolean requiresRedirect) {
        this.requiresRedirect = requiresRedirect;
        return this;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }
}
