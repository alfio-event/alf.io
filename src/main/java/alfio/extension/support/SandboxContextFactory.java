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
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Scriptable;

// source: https://codeutopia.net/blog/2009/01/02/sandboxing-rhino-in-java/
//         https://www-archive.mozilla.org/rhino/apidocs/org/mozilla/javascript/contextfactory
public class SandboxContextFactory extends ContextFactory {

    // Custom Context to store execution time.
    private static class MyContext extends Context {
        long startTime;

        public MyContext(ContextFactory factory) {
            super(factory);

        }
    }

    @Override
    protected Context makeContext() {
        MyContext cx = new MyContext(ContextFactory.getGlobal());
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
        if (executionTime > 15*1000) {
            // More than 15 seconds from Context creation time:
            // it is time to stop the script.
            // Throw Error instance to ensure that script will never
            // get control back through catch or finally.
            throw new ExecutionTimeoutException("Script execution timeout.");
        }
    }

    @Override
    protected Object doTopCall(Callable callable, Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
        MyContext mcx = (MyContext) cx;
        mcx.startTime = System.currentTimeMillis();
        return super.doTopCall(callable, cx, scope, thisObj, args);
    }
}
