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

import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Ram Kulkarni
 * http://ramkulkarni.com/blog/parsing-javascript-code-using-mozilla-rhino/
 *
 * Implements ErrorReporter interface of Rhino and prints syntax errors in the JS script.
 */
public class JSErrorReporter implements ErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(JSErrorReporter.class);

    @Override
    public void warning(String message, String sourceName, int line, String lineSource, int lineOffset) {
        log.warn("Warning : {}", message);
    }

    @Override
    public EvaluatorException runtimeError(String message, String sourceName, int line, String lineSource, int lineOffset) {
        return new EvaluatorException(message, sourceName, line, lineSource, lineOffset);
    }

    @Override
    public void error(String message, String sourceName, int line, String lineSource, int lineOffset) {
        log.warn("Error : {}", message);
    }

}
