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

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static alfio.model.system.ConfigurationKeys.SECURITY_CSP_REPORT_ENABLED;
import static alfio.model.system.ConfigurationKeys.SECURITY_CSP_REPORT_URI;


@RestController
public class CspReportApiController {


    private static final Logger log = LoggerFactory.getLogger(CspReportApiController.class);
    private final ConfigurationManager configurationManager;

    public CspReportApiController(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    @PostMapping("/report-csp-violation")
    public boolean logCspViolation(HttpServletRequest request) throws IOException {

        var conf = configurationManager.getFor(Set.of(SECURITY_CSP_REPORT_ENABLED, SECURITY_CSP_REPORT_URI), ConfigurationLevel.system());

        boolean enabledReport = conf.get(SECURITY_CSP_REPORT_ENABLED).getValueAsBooleanOrDefault();

        String uri = conf.get(SECURITY_CSP_REPORT_URI).getValueOrDefault("/report-csp-violation");

        if (enabledReport && "/report-csp-violation".equals(uri)) {
            String report = request.getReader().lines().collect(Collectors.joining(" _ "));
            log.warn("found csp violation: {}", report);
        }
        return true;
    }
}
