package alfio.extension;

import alfio.extension.exception.ScriptNotValidException;
import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;


public class ScriptValidationTest {

    private String getScriptContent(String file) throws IOException {
        String concatenation;
        try(var input = getClass().getResourceAsStream("/rhino-scripts/" + file)) {
            List<String> extensionStream = IOUtils.readLines(new InputStreamReader(input, StandardCharsets.UTF_8));
            concatenation = String.join("\n", extensionStream)+"\n;executeScript(extensionEvent)";
        }
        return concatenation;
    }

    @Test
    void testBaseScriptValidation() throws Exception {
        String concatenation = getScriptContent("base.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertTrue(validation.validate());
    }

    @Test
    @Ignore
    void testBoundariesExitValidation() throws Exception {
        String concatenation = getScriptContent("boundariesExit.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertTrue(validation.validate());
    }

    @Test
    void testWhileLoopValidation() throws Exception {
        try {
            String concatenation = getScriptContent("timeout.js");
            ScriptValidation validation = new ScriptValidation(concatenation);
            Assertions.assertTrue(validation.validate());
        } catch (Exception ex) {
            assertTrue(ex instanceof ScriptNotValidException);
        }
    }
}
