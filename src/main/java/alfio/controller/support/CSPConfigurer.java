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
package alfio.controller.support;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.util.List;

import static alfio.model.system.ConfigurationKeys.*;

@Component
public class CSPConfigurer {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ConfigurationManager configurationManager;

    public CSPConfigurer(ConfigurationManager configurationManager) {
        this.configurationManager = configurationManager;
    }

    public String addCspHeader(HttpServletResponse response, boolean embeddingSupported) {
        return addCspHeader(response, ConfigurationLevel.system(), embeddingSupported);
    }

    public String addCspHeader(HttpServletResponse response, ConfigurationLevel configurationLevel, boolean embeddingSupported) {

        var nonce = getNonce();

        String reportUri = "";

        var conf = configurationManager.getFor(List.of(SECURITY_CSP_REPORT_ENABLED, SECURITY_CSP_REPORT_URI, EMBED_ALLOWED_ORIGINS), configurationLevel);

        boolean enabledReport = conf.get(SECURITY_CSP_REPORT_ENABLED).getValueAsBooleanOrDefault();
        if (enabledReport) {
            reportUri = " report-uri " + conf.get(SECURITY_CSP_REPORT_URI).getValueOrDefault("/report-csp-violation");
        }
        //
        // https://csp.withgoogle.com/docs/strict-csp.html
        // with base-uri set to 'self'

        var frameAncestors = "'none'";
        var allowedContainer = conf.get(EMBED_ALLOWED_ORIGINS).getValueOrNull();
        if (embeddingSupported && StringUtils.isNotBlank(allowedContainer)) {
            var splitHosts = allowedContainer.split("[,\n]");
            frameAncestors = String.join(" ", splitHosts);
            // IE11
            response.addHeader("X-Frame-Options", "ALLOW-FROM "+splitHosts[0]);
        } else {
            response.addHeader("X-Frame-Options", "DENY");
        }

        response.addHeader("Content-Security-Policy", "object-src 'none'; "+
            "script-src 'strict-dynamic' 'nonce-" + nonce + "' 'unsafe-inline' http: https: " +
            "'unsafe-hashes' 'sha256-MhtPZXr7+LpJUY5qtMutB+qWfQtMaPccfe7QXtCcEYc='" // see https://github.com/angular/angular-cli/issues/20864#issuecomment-983672336
            +"; " +
            "base-uri 'self'; " +
            "frame-ancestors " + frameAncestors + "; "
            + reportUri);

        return nonce;
    }

    private static String getNonce() {
        var nonce = new byte[16]; //128 bit = 16 bytes
        SECURE_RANDOM.nextBytes(nonce);
        return Hex.encodeHexString(nonce);
    }
}
