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
package alfio.controller.api.support;

import alfio.manager.system.ConfigurationManager;
import alfio.model.system.Configuration;
import alfio.model.system.ConfigurationKeys;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.stream.Collectors;


@RestController
@Log4j2
@AllArgsConstructor
public class CspReportApiController {


    private final ConfigurationManager configurationManager;

    @RequestMapping(value = "/report-csp-violation", method = RequestMethod.POST)
    public boolean logCspViolation(HttpServletRequest request) throws IOException {


        boolean enabledReport = configurationManager.getBooleanConfigValue(
            Configuration.getSystemConfiguration(ConfigurationKeys.SECURITY_CSP_REPORT_ENABLED), false);

        String uri = configurationManager.getStringConfigValue(
            Configuration.getSystemConfiguration(ConfigurationKeys.SECURITY_CSP_REPORT_URI), "/report-csp-violation");

        if (enabledReport && "/report-csp-violation".equals(uri)) {
            String report = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            log.warn("found csp violation: {}", report);
        }
        return true;
    }
}
