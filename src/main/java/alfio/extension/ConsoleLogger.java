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

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.stream.Stream;

@Log4j2
public class ConsoleLogger {
    private final ExtensionLogger extensionLogger;

    public ConsoleLogger(ExtensionLogger extensionLogger) {
        this.extensionLogger = extensionLogger;
    }

    public void log(Object first, Object... others) {
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
        var message = String.format(messageTemplate + parameterPlaceholders, paramObjects);
        log.info(message);
        extensionLogger.logInfo(message);
    }
}
