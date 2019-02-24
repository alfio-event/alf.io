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
package alfio.util;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Log4j2
@UtilityClass
public class RequestUtils {

    public static Optional<String> readRequest(HttpServletRequest request) {
        try (ServletInputStream is = request.getInputStream()){
            return Optional.ofNullable(is.readAllBytes()).map(b -> new String(b, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("exception during request conversion", e);
            return Optional.empty();
        }
    }
}
