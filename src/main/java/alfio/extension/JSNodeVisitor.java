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

import java.util.ArrayDeque;
import java.util.ArrayList;

import alfio.extension.exception.ScriptNotValidException;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;

/**
 *
 * @author Ram Kulkarni
 * http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/
 *
 * Implements Rhino’s NodeVisitor interface and builds hierarchy of JSSymbol
 */
public class JSNodeVisitor implements NodeVisitor {
    ArrayDeque<JSSymbol> functionsStack = new ArrayDeque<>();
    int currentFuncEndOffset = -1;
    JSSymbol root = null;
    ArrayList<String> functionCalls = new ArrayList<>();

    @Override
    public boolean visit(AstNode node) {
        if (node == null) {
            return false;
        }

        addToParent(node);
        return true;
    }

    private void addToParent(AstNode node) {
        if (root == null) {
            root = new JSSymbol(node);
            functionsStack.addFirst(root);
            currentFuncEndOffset = node.getAbsolutePosition() + node.getLength();
            return;
        }
        if (functionsStack.size() == 0) {
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
            if (isVariableName(node)) {
                // check if it is in the current function
                String symbolName = ((Name) node).getIdentifier();
                JSSymbol currentSymContainer = functionsStack.peekFirst();
                if (!currentSymContainer.childExist(symbolName)) {
                    //this is a global symbol
                    root.addChild(node);
                }
            }
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
                            throw new ScriptNotValidException("Script not valid.");
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
            throw new ScriptNotValidException("Script not valid.");
        }
        JSSymbol currSym = null;
        JSSymbol parent = functionsStack.peekFirst();
        if (parent.getNode().getAbsolutePosition() + parent.getNode().getLength() > node.getAbsolutePosition()) {
            //add child node to parent
            currSym = new JSSymbol(node);
            parent.addChild(currSym);
        } else { //outside current function boundary
            //pop current parent
            functionsStack.removeFirst();
            addToParent(node);
            return;
        }

        //currSym is already set above
        if (nodeType == Token.FUNCTION || nodeType == Token.OBJECTLIT) {
            AstNode parentNode = node.getParent();
            AstNode leftNode = null;
            if (parentNode.getType() == Token.ASSIGN) {
                leftNode = ((Assignment)parentNode).getLeft();
            } else if (parentNode instanceof ObjectProperty) {
                leftNode = ((ObjectProperty)parentNode).getLeft();
            }
            if (leftNode instanceof Name) {
                currSym.setName(((Name) leftNode).getIdentifier());
            }
            functionsStack.addFirst(currSym);
            currentFuncEndOffset = node.getAbsolutePosition() + node.getLength();
        }
    }

    //This is a helper function to get variables used outside
    //variable initializer
    private boolean isVariableName (AstNode node) {
        AstNode parentNode = node.getParent();
        if (parentNode == null || !(node instanceof Name)) {
            return false;
        }
        int parentType =  parentNode.getType();
        if (parentType == Token.GETPROP)  { //get only the left most variable
            return (((PropertyGet)parentNode).getLeft() == node);
        }
        return (parentType != Token.FUNCTION && parentType != Token.VAR);
    }

    public JSSymbol getRoot() {
        return root;
    }
}
