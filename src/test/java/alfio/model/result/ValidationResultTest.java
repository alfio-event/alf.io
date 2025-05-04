package alfio.model.result;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void checkErrorDescriptorToString() {
        Assertions.assertEquals("ValidationResult.ErrorDescriptor(fieldName=fieldName, message=message, code=code, arguments=[a, b])", new ValidationResult.ErrorDescriptor("fieldName", "message", "code", new Object[]{"a", "b"}).toString());
    }
}
