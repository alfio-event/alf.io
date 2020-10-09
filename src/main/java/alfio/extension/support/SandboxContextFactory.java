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
package alfio.extension.support;

import alfio.extension.exception.ExecutionTimeoutException;
import org.mozilla.javascript.*;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
//         https://www-archive.mozilla.org/rhino/apidocs/org/mozilla/javascript/contextfactory
public class SandboxContextFactory extends ContextFactory {

    // Custom Context to store execution time.
    private static class MyContext extends Context {
        long startTime;
    }

    static {
        // Initialize GlobalFactory with custom factory
        ContextFactory.initGlobal(new SandboxContextFactory());
    }

    @Override
    protected Context makeContext() {
//        Context cx = super.makeContext();
        MyContext cx = new MyContext();
        cx.setWrapFactory(new SandboxWrapFactory());
        // Use pure interpreter mode to allow for observeInstructionCount(Context, int) to work
        cx.setOptimizationLevel(-1);
        // Make Rhino runtime to call observeInstructionCount each 10000 bytecode instructions
        cx.setInstructionObserverThreshold(10000);
        return cx;
    }

    @Override
    protected void observeInstructionCount(Context cx, int instructionCount) {
        MyContext mcx = (MyContext)cx;
        long currentTime = System.currentTimeMillis();
        long executionTime = currentTime - mcx.startTime;
        if (executionTime > 10*1000) {
            // More than 10 seconds from Context creation time:
            // it is time to stop the script.
            // Throw Error instance to ensure that script will never
            // get control back through catch or finally.
            throw new ExecutionTimeoutException("Script execution timeout.");
        }
    }

    @Override
    protected boolean hasFeature(Context cx, int featureIndex) {
        switch (featureIndex) {
            case Context.FEATURE_NON_ECMA_GET_YEAR:
                return true;
            case Context.FEATURE_MEMBER_EXPR_AS_FUNCTION_NAME:
                return true;
            case Context.FEATURE_RESERVED_KEYWORD_AS_IDENTIFIER:
                return true;
            case Context.FEATURE_PARENT_PROTO_PROPERTIES:
                return false;
        }
        return super.hasFeature(cx, featureIndex);
    }

    @Override
    protected Object doTopCall(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        MyContext mcx = (MyContext) cx;
        mcx.startTime = System.currentTimeMillis();
        return super.doTopCall(callable, cx, scope, thisObj, args);
    }
}
