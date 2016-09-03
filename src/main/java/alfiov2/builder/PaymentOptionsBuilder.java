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
package alfiov2.builder;

import alfio.model.transaction.PaymentProxy;

import java.math.BigDecimal;
import java.util.List;


public class PaymentOptionsBuilder {

    public static PaymentOptionsBuilder of(BigDecimal price, String currency) {
        return new PaymentOptionsBuilder();
    }

    public static PaymentOptions none() {
        return new PaymentOptions();
    }

    public PaymentOptionsBuilder vatIncluded(BigDecimal vat) {
        //vat status = included
        return this;
    }

    public PaymentOptionsBuilder vatNotIncluded(BigDecimal vat) {
        return this;
    }

    public PaymentOptions enableMethods(List<PaymentProxy> options) {
        return new PaymentOptions();
    }


    public static class PaymentOptions {
    }
}
