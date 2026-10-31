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
import alfio.repository.system.AdminJobQueueRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The JavaScript syntax accepted by the extension scripts: a script is saved only if it passes the validation
 * (ScriptValidation) and runs (ScriptingExecutionService), see ExtensionService.createOrUpdate.
 * <p>
 * The admin script editor proposes only the supported keywords and snippets
 * (frontend/admin/src/extension/script-completions.ts): update it when these cases change, e.g. after a Rhino upgrade.
 */
class ScriptSyntaxSupportTest {

    private static ScriptingExecutionService scriptingExecutionService;

    @BeforeAll
    static void init() {
        Supplier<Executor> executorSupplier = () -> Runnable::run;
        scriptingExecutionService = new ScriptingExecutionService(Mockito.mock(HttpClient.class), Mockito.mock(AdminJobQueueRepository.class), executorSupplier);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "var a = 1;",
        "let a = 1; a = 2;",
        "const a = 1;",
        "for (let i = 0; i < 2; i++) { continue; }",
        "for (let v of [1, 2]) { }",
        "for (var k in {a: 1}) { }",
        "if (true) { } else { }",
        "switch (1) { case 1: break; default: break; }",
        "try { throw new Error('x'); } catch (e) { } finally { }",
        "try { throw new Error('x'); } catch { }",
        "var o = {a: 1}; typeof o; o instanceof Object; 'a' in o; delete o.a; new Object(); this;",
        "var t = true, f = false, n = null;",
        "function f(a = 1, ...rest) { return a + rest.length; } f();",
        "var f = (a) => a + 1; f(1);",
        "var s = `a${1}`;",
        "var {a} = {a: 1}; var [b, c] = [1, 2];",
        "var a = [...[1, 2]]; var o = {...{a: 1}};",
        "var k = 'a'; var o = {k, [k]: 1, m() { return 1; }, get g() { return 1; }};",
        "var x = 2 ** 3;",
        "var o = null; var x = o?.a ?? 1; x ??= 2;",
        "function* g() { yield 1; } g().next();",
    })
    void supported(String script) {
        assertDoesNotThrow(() -> new ScriptValidation(script).validate());
        assertDoesNotThrow(() -> execute(script));
    }

    // Rhino runs them, the validation rejects them on purpose (JSNodeVisitor)
    @ParameterizedTest
    @ValueSource(strings = {
        "var i = 0; while (i < 1) { i++; }",
        "var i = 0; do { i++; } while (i < 1);",
        "outer: for (var i = 0; i < 1; i++) { break outer; }",
        "with ({a: 1}) { a; }",
        // the body of a catch without binding is validated too
        "try { } catch { while (true) { } }",
    })
    void rejectedByValidation(String script) {
        assertThrows(ScriptNotValidException.class, () -> new ScriptValidation(script).validate());
    }

    // not supported by Rhino
    @ParameterizedTest
    @ValueSource(strings = {
        "class A { constructor(a) { this.a = a; } }",
        "class A { } class B extends A { constructor() { super(); } }",
        "class A { static m() { return 1; } }",
        "async function f() { await 1; }",
        "import {a} from 'b';",
        "export function f() { }",
        "for (const v of [1, 2]) { }",
    })
    void unsupported(String script) {
        assertThrows(Exception.class, () -> execute(script));
    }

    private void execute(String script) {
        scriptingExecutionService.executeScript("syntax", script, Map.of(), Object.class, Mockito.mock(ExtensionLogger.class));
    }
}
