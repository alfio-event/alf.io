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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PaymentResultTest {

    @Test
    void checkToString() {
        Assertions.assertEquals("PaymentResult(type=SUCCESSFUL, gatewayId=Optional[gatewayId], errorCode=Optional.empty, redirectUrl=null)", PaymentResult.successful("gatewayId").toString());
        Assertions.assertEquals("PaymentResult(type=FAILED, gatewayId=Optional.empty, errorCode=Optional[fail], redirectUrl=null)",PaymentResult.failed("fail").toString());
        Assertions.assertEquals("PaymentResult(type=INITIALIZED, gatewayId=Optional[init], errorCode=Optional.empty, redirectUrl=null)",PaymentResult.initialized("init").toString());
        Assertions.assertEquals("PaymentResult(type=PENDING, gatewayId=Optional[pending], errorCode=Optional.empty, redirectUrl=null)",PaymentResult.pending("pending").toString());
        Assertions.assertEquals("PaymentResult(type=REDIRECT, gatewayId=Optional.empty, errorCode=Optional.empty, redirectUrl=redirectUrl)",PaymentResult.redirect("redirectUrl").toString());
    }
}
