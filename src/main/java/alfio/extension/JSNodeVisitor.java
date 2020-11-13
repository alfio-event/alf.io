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

import java.util.Stack;

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
    Stack<JSSymbol> functionsStack = new Stack<>();
    int currentFuncEndOffset = -1;
    JSSymbol root = null;

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
            functionsStack.push(root);
            currentFuncEndOffset = node.getAbsolutePosition() + node.getLength();
            return;
        }
        if (functionsStack.size() == 0) {
            return;
        }
        int nodeType = node.getType();
        // we will track only variables and functions
        // add function calls, loops, while
        if (nodeType != Token.FUNCTION && nodeType != Token.WITH && nodeType != Token.LABEL && nodeType != Token.VAR && nodeType != Token.NAME && nodeType != Token.WHILE && nodeType != Token.DO && nodeType != Token.OBJECTLIT && nodeType != Token.CALL && nodeType != Token.GETPROP &&
            !(nodeType == Token.NAME && node.getParent() instanceof ObjectProperty)) {
            if (isVariableName(node)) {
                // check if it is in the current function
                String symbolName = ((Name)node).getIdentifier();
                JSSymbol currentSymContainer = functionsStack.peek();
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
        if(node instanceof WhileLoop || node instanceof DoLoop || node instanceof WithStatement || node instanceof LabeledStatement) {
            throw new ScriptNotValidException("Script not valid.");
        }
        JSSymbol currSym = null;
        JSSymbol parent = functionsStack.peek();
        if (parent.getNode().getAbsolutePosition() + parent.getNode().getLength() > node.getAbsolutePosition()) {
            //add child node to parent
            currSym = new JSSymbol(node);
            parent.addChild(currSym);
        } else { //outside current function boundary
            //pop current parent
            functionsStack.pop();
            addToParent(node);
            return;
        }

        //currSym is already set above
        if (nodeType == Token.FUNCTION || nodeType == Token.OBJECTLIT || nodeType == Token.CALL) {
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
            functionsStack.push(currSym);
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
        return (parentType != Token.FUNCTION &&
            parentType != Token.VAR &&
            parentType != Token.CALL
        );
    }

    public JSSymbol getRoot() {
        return root;
    }
}
