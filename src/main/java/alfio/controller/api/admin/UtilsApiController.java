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
import alfio.util.MustacheCustomTag;
import alfio.util.Wrappers;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.money.CurrencyUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/utils")
@Log4j2
public class UtilsApiController {

    private static final List<String> CURRENCIES_BLACKLIST = Arrays.asList("USN", "USS", "CHE", "CHW");
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

    @GetMapping("/short-name/generate")
    public String generateShortName(@RequestParam("displayName") String displayName) {
        return eventNameManager.generateShortName(displayName);
    }

    @PostMapping("/short-name/validate")
    public boolean validateShortName(@RequestParam("shortName") String shortName, HttpServletResponse response) {
        boolean unique = eventNameManager.isUnique(shortName);
        if(!unique) {
            response.setStatus(HttpServletResponse.SC_CONFLICT);
        }
        return unique;
    }
    
    @GetMapping("/render-commonmark")
    public String renderCommonmark(@RequestParam("text") String input) {
        return MustacheCustomTag.renderToHtmlCommonmarkEscaped(input);
    }

    @GetMapping("/alfio/info")
    public Map<String, Object> getApplicationInfo(Principal principal) {
        Map<String, Object> applicationInfo = new HashMap<>();
        applicationInfo.put("version", version);
        applicationInfo.put("username", principal.getName());
        applicationInfo.put("isDemoMode", environment.acceptsProfiles(Profiles.of(Initializer.PROFILE_DEMO)));
        return applicationInfo;
    }

    @GetMapping("/currencies")
    public List<CurrencyDescriptor> getCurrencies() {
        return CurrencyUnit.registeredCurrencies().stream()
            //we don't support pseudo currencies, as it is very unlikely that payment providers would support them
            .filter(c -> !c.isPseudoCurrency() && !CURRENCIES_BLACKLIST.contains(c.getCode()) && Wrappers.optionally(() -> Currency.getInstance(c.getCode())).isPresent())
            .map(c -> new CurrencyDescriptor(c.getCode(), c.toCurrency().getDisplayName(Locale.ENGLISH), c.getSymbol(Locale.ENGLISH), c.getDecimalPlaces()))
            .collect(Collectors.toList());
    }

    @GetMapping("/countriesForVat")
    public Map<String, String> getCountriesForVat() {
        return TicketHelper.getLocalizedCountriesForVat(Locale.ENGLISH)
            .stream()
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
    }

}
