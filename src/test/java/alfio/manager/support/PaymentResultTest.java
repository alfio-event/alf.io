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
