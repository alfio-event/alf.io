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
package alfio.extension;

import alfio.extension.exception.ScriptNotValidException;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;


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
        Assertions.assertDoesNotThrow(validation::validate);
    }

    @Test
    void testBoundariesExitValidation() throws IOException {
        String concatenation = getScriptContent("boundariesExit.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testBoundariesReflectionValidation() throws IOException {
        String concatenation = getScriptContent("boundariesReflection.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testWhileLoopValidation() throws IOException {
        String concatenation = getScriptContent("timeout.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testDoLoopValidation() throws IOException {
        String concatenation = getScriptContent("doLoop.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testWithStatementValidation() throws IOException {
        String concatenation = getScriptContent("withStatement.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testLabeledStatementValidation() throws IOException {
        String concatenation = getScriptContent("labeledStatement.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testFunctionLevelsValidation() throws IOException {
        String concatenation = getScriptContent("functionLevels.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertThrows(ScriptNotValidException.class, validation::validate);
    }

    @Test
    void testFunctionLevelsLegitValidation() throws IOException {
        String concatenation = getScriptContent("functionLegitLevels.js");
        ScriptValidation validation = new ScriptValidation(concatenation);
        Assertions.assertDoesNotThrow(validation::validate);
    }
}
