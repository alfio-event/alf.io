package alfio.controller.api.support;

import alfio.config.Initializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile(Initializer.PROFILE_DEBUG_CSP)
@Log4j2
public class CspReportApiController {
    @RequestMapping(value = "/report-csp-violation", method = RequestMethod.POST)
    public boolean logCspViolation(@RequestBody ObjectNode report) {
        log.warn("found csp violation: {}", report);
        return true;
    }
}
