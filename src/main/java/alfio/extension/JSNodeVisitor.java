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
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;

import java.util.ArrayList;

/**
 *
 * @author Ram Kulkarni
 * http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/
 *
 * Implements Rhino’s NodeVisitor interface and builds hierarchy of JSSymbol
 */
class JSNodeVisitor implements NodeVisitor {
    private JSSymbol root = null;
    private final ArrayList<String> functionCalls = new ArrayList<>();

    @Override
    public boolean visit(AstNode node) {
        if (node == null) {
            return false;
        }
        checkNode(node);
        return true;
    }

    private void checkNode(AstNode node) {
        if (root == null) {
            root = new JSSymbol(node);
            return;
        }
        int nodeType = node.getType();
        // we will track variables and functions, function calls, loops,
        // with statement, labeled statement, level of function calls
        if (nodeType != Token.FUNCTION
            && nodeType != Token.WITH
            && nodeType != Token.LABEL
            && nodeType != Token.VAR
            && nodeType != Token.NAME
            && nodeType != Token.WHILE
            && nodeType != Token.DO
            && nodeType != Token.OBJECTLIT
            && nodeType != Token.CALL
            && nodeType != Token.GETPROP
            && nodeType != Token.EXPR_VOID) {
            return;
        }
        if (node.getType() == Token.VAR && !(node instanceof VariableInitializer)) {
            return;
        }
        // keep track of function calls
        if (node instanceof FunctionCall) {
            AstNode target = ((FunctionCall) node).getTarget();
            if (!(target instanceof PropertyGet)) {
                Name name = (Name) target;
                // keep all function calls inside an ArrayList
                functionCalls.add(name.getIdentifier());
                // go back in the script and find the parent i.e. the function in which this node is inside
                AstNode parentNode = node.getParent();
                while (parentNode != null) {
                    if (parentNode instanceof FunctionNode) {
                        Name parentName = ((FunctionNode) parentNode).getFunctionName();
                        String id = parentName.getIdentifier();
                        // when the function name is found, check if it was called from somewhere else
                        // if this function is called from another place and it contains another function call, throw an exception
                        if (functionCalls.contains(id)) {
                            throw new ScriptNotValidException("Script not valid. Cannot call nested functions: "+name.getIdentifier());
                        }
                        break;
                    }
                    parentNode = parentNode.getParent();
                }
            }
        }
        if (node instanceof WhileLoop
            || node instanceof DoLoop
            || node instanceof WithStatement
            || node instanceof LabeledStatement
            || (node instanceof PropertyGet && ((PropertyGet) node).getRight().getString().equals("System"))
            || (node instanceof PropertyGet && ((PropertyGet) node).getRight().getString().equals("getClass"))
            || (node instanceof Name && node.getString().equals("newInstance"))) {
            throw new ScriptNotValidException("Script not valid. One or more of the following components have been detected: \n" +
                "- while() Loop\n" +
                "- with() Statement\n" +
                "- a labeled statement\n" +
                "- Access to java.lang.System\n" +
                "- Access to Object.getClass()\n" +
                "- Java reflection usage");
        }
    }

    public JSSymbol getRoot() {
        return root;
    }
}
