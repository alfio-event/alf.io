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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.*;

/**
 *
 * @author Ram Kulkarni
 * http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/
 *
 * This class holds reference to AST node and child elements.
 */
public class JSSymbol {
    private final AstNode node;
    private final ArrayList<JSSymbol> children = new ArrayList<>();
    private final Map<String, JSSymbol> localVars = new HashMap<String, JSSymbol>();
    private String name = null;
    private JSSymbol parent = null;

    public JSSymbol(AstNode node) {
        this.node = node;
        if (node instanceof VariableInitializer) {
            name = ((Name) ((VariableInitializer) node).getTarget()).getIdentifier();
        } else if (node instanceof FunctionNode) {
            Name funcName = ((FunctionNode)node).getFunctionName();
            if (funcName != null) {
                name = funcName.getIdentifier();
            } else {
                AstNode parent = node.getParent();
                if (parent instanceof VariableInitializer) {
                    name = ((Name)((VariableInitializer)parent).getTarget()).getIdentifier();
                } else {
                    name = "Anonymous";
                }
            }
            FunctionNode funcNode = (FunctionNode)node;
            List<AstNode> args = funcNode.getParams();
            if (args != null) {
                for (AstNode argNode : args) {
                    addChild(argNode);
                }
            }
        } else if (node instanceof Name) {
            name = ((Name) node).getIdentifier();
        }
    }

    public int getType() {
        return node.getType();
    }

    public void addChild(JSSymbol child) {
        if (child.getType() == Token.VAR) {
            //check if it is already added
            AstNode childNode = child.getNode();
            if (childNode instanceof VariableInitializer) {
                String varName = ((Name)((VariableInitializer) childNode).getTarget()).getIdentifier();
                if (localVars.containsKey(varName)) {
                    return;
                }
                localVars.put(varName, child);
            }
        }
        children.add(child);
        child.setParent(this);
    }

    public void addChild (AstNode node) {
        addChild(new JSSymbol(node));
    }

    public ArrayList<JSSymbol> getChildren() {
        return new ArrayList<>(children);
    }

    public AstNode getNode() {
        return node;
    }

    public boolean childExist (String name) {
        return localVars.containsKey(name);
    }

    public String getName() {
        return name;
    }

    public boolean visit () {
        boolean ret = this.getNode() instanceof AstRoot || this.getNode().getType() == Token.FUNCTION;
//        boolean ret = visitor.visit(this);
        if (ret) {
            for (JSSymbol child : children) {
                child.visit();
            }
            return true;
        } else {
            return false;
        }
    }

    public JSSymbol getParent() {
        return parent;
    }

    public void setParent(JSSymbol parent) {
        this.parent = parent;
    }

    void setName(String name) {
        this.name = name;
    }
}
