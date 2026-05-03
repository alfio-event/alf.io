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

import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.HostFunction;

@Builtins("extensionLogger")
class ExtensionLoggerApi {
    private final ExtensionLogger logger;

    ExtensionLoggerApi(ExtensionLogger logger) {
        this.logger = logger;
    }

    @HostFunction
    public void logInfo(String msg) { logger.logInfo(msg); }

    @HostFunction
    public void logWarning(String msg) { logger.logWarning(msg); }

    @HostFunction
    public void logError(String msg) { logger.logError(msg); }

    @HostFunction
    public void logSuccess(String msg) { logger.logSuccess(msg); }
}
