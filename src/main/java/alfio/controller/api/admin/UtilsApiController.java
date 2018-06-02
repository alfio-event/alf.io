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
package alfio.controller.api.admin;

import alfio.config.Initializer;
import alfio.controller.api.support.CurrencyDescriptor;
import alfio.controller.api.support.TicketHelper;
import alfio.manager.EventNameManager;
import alfio.util.MustacheCustomTagInterceptor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

@RestController
@RequestMapping("/admin/api/utils")
@Log4j2
public class UtilsApiController {

    private static final List<String> CURRENCIES_BLACKLIST = Arrays.asList("USN", "USS");
    private final EventNameManager eventNameManager;
    private final String version;
    private final Environment environment;

    @Autowired
    public UtilsApiController(EventNameManager eventNameManager, @Value("${alfio.version}") String version, Environment environment) {
        this.eventNameManager = eventNameManager;
        this.version = version;
        this.environment = environment;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<String> handleMissingServletRequestParameterException(Exception e) {
        log.warn("missing parameters", e);
        return new ResponseEntity<>("missing parameters", HttpStatus.BAD_REQUEST);
    }

    @RequestMapping(value = "/short-name/generate", method = GET)
    public String generateShortName(@RequestParam("displayName") String displayName) {
        return eventNameManager.generateShortName(displayName);
    }

    @RequestMapping(value = "/short-name/validate", method = POST)
    public boolean validateShortName(@RequestParam("shortName") String shortName, HttpServletResponse response) {
        boolean unique = eventNameManager.isUnique(shortName);
        if(!unique) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
        return unique;
    }
    
    @RequestMapping(value = "/render-commonmark") 
    public String renderCommonmark(@RequestParam("text") String input) {
    	return MustacheCustomTagInterceptor.renderToCommonmark(StringEscapeUtils.escapeHtml4(input));
    }

    @RequestMapping(value = "/alfio/info", method = GET)
    public Map<String, Object> getApplicationInfo(Principal principal) {
        Map<String, Object> applicationInfo = new HashMap<>();
        applicationInfo.put("version", version);
        applicationInfo.put("username", principal.getName());
        applicationInfo.put("isDemoMode", environment.acceptsProfiles(Initializer.PROFILE_DEMO));
        return applicationInfo;
    }

    @RequestMapping(value = "/currencies", method = GET)
    public List<CurrencyDescriptor> getCurrencies() {
        return Currency.getAvailableCurrencies().stream()
            .filter(c -> c.getDefaultFractionDigits() == 2 && !CURRENCIES_BLACKLIST.contains(c.getCurrencyCode())) //currencies which don't support cents are filtered out. Support will be implemented in the next version
            .map(c -> new CurrencyDescriptor(c.getCurrencyCode(), c.getDisplayName(), c.getSymbol(), c.getDefaultFractionDigits()))
            .collect(Collectors.toList());
    }

    @RequestMapping(value = "/countriesForVat", method = GET)
    public Map<String, String> getCountriesForVat() {
        return TicketHelper.getLocalizedCountriesForVat(Locale.ENGLISH)
            .stream()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

}
