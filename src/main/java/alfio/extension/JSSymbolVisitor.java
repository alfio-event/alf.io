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

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;

/**
 *
 * @author Ram Kulkarni
 * http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/
 *
 * Prints hierarchy of JSSymbol objects.
 */
public class JSSymbolVisitor {

    public boolean visit(JSSymbol sym) {
        AstNode astNode = sym.getNode();
        if (astNode instanceof AstRoot) {
            return true;
        }
        int tabs = getNumTabs(sym);
        for (int i = 0; i < tabs; i++) {
            System.out.print("\t");
        }
        if (astNode.getType() == Token.FUNCTION) {
            System.out.print("Function : ");
        }
        System.out.println(sym.getName());
        return true;
    }

    private int getNumTabs(JSSymbol sym) {
        int tabs = 0;
        JSSymbol currSym = sym;
        while ((currSym = currSym.getParent()) != null) {
            tabs++;
        }
        return tabs;
    }
}
