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

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.stream.Stream;

@Slf4j
public class ConsoleLogger {
    private final ExtensionLogger extensionLogger;

    public ConsoleLogger(ExtensionLogger extensionLogger) {
        this.extensionLogger = extensionLogger;
    }

    public void log(Object first, Object... others) {
        var message = composeMessage(first, others);
        log.info(message);
        extensionLogger.logInfo(message);
    }

    public void warn(Object first, Object... others) {
        var message = composeMessage(first, others);
        log.warn(message);
        extensionLogger.logWarning(message);
    }

    public void error(Object first, Object... others) {
        var message = composeMessage(first, others);
        log.error(message);
        extensionLogger.logError(message);
    }

    private static String composeMessage(Object first, Object... others) {
        String messageTemplate = "%s";
        var parameterPlaceholders = "";
        Object[] paramObjects;
        if(others != null) {
            parameterPlaceholders = " %s".repeat(others.length);
            paramObjects = Stream.concat(Stream.of(first), Arrays.stream(others))
                .map(ExtensionUtils::unwrap)
                .toArray();
        } else {
            paramObjects = new Object[] {first};
        }
        return String.format(messageTemplate + parameterPlaceholders, paramObjects);
    }
}
