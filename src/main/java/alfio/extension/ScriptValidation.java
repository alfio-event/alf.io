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

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstRoot;

// http://ramkulkarni.com/blog/understanding-ast-created-by-mozilla-rhino-parser/
// http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/

// https://github.com/mozilla/rhino/tree/master/src/org/mozilla/javascript/ast
public class ScriptValidation {

    private final String script;

    public ScriptValidation(String script) {
        this.script = script;
    }

    public void validate() {
        CompilerEnvirons env = new CompilerEnvirons();
        env.setRecoverFromErrors(true);

        Parser parser = new Parser(env, new JSErrorReporter());
        AstRoot rootNode = parser.parse(script, null, 0);

        JSNodeVisitor nodeVisitor = new JSNodeVisitor();
        rootNode.visit(nodeVisitor);
    }

}

