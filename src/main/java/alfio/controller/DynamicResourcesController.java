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
package alfio.controller;

import alfio.manager.system.ConfigurationManager;
import alfio.model.Event;
import alfio.model.system.Configuration;
import alfio.model.system.Configuration.ConfigurationPathKey;
import alfio.repository.EventRepository;
import alfio.util.TemplateManager;
import alfio.util.TemplateResource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;

import static alfio.model.system.ConfigurationKeys.GOOGLE_ANALYTICS_ANONYMOUS_MODE;
import static alfio.model.system.ConfigurationKeys.GOOGLE_ANALYTICS_KEY;

@Controller
public class DynamicResourcesController {

    private static final String GOOGLE_ANALYTICS_SCRIPT = "var _gaq = _gaq || [];_gaq.push(['_setAccount', '%s']);_gaq.push(['_trackPageview']);";
    private static final String EMPTY = "(function(){})();";
    private final ConfigurationManager configurationManager;
    private final TemplateManager templateManager;
    private final EventRepository eventRepository;

    @Autowired
    public DynamicResourcesController(ConfigurationManager configurationManager, TemplateManager templateManager, EventRepository eventRepository) {
        this.configurationManager = configurationManager;
        this.templateManager = templateManager;
        this.eventRepository = eventRepository;
    }

    @RequestMapping("/resources/js/google-analytics")
    public void getGoogleAnalyticsScript(HttpSession session, HttpServletResponse response, @RequestParam("e") Integer eventId) throws IOException {
        response.setContentType("application/javascript");
        Optional<Event> ev = Optional.ofNullable(eventId).flatMap(id -> Optional.ofNullable(eventRepository.findById(id)));
        ConfigurationPathKey pathKey = ev.map(e -> Configuration.from(e.getOrganizationId(), e.getId(), GOOGLE_ANALYTICS_KEY)).orElseGet(() -> Configuration.getSystemConfiguration(GOOGLE_ANALYTICS_KEY));
        final Optional<String> id = configurationManager.getStringConfigValue(pathKey);
        final String script;
        ConfigurationPathKey anonymousPathKey = ev.map(e -> Configuration.from(e.getOrganizationId(), e.getId(), GOOGLE_ANALYTICS_ANONYMOUS_MODE)).orElseGet(() -> Configuration.getSystemConfiguration(GOOGLE_ANALYTICS_ANONYMOUS_MODE));
        if(id.isPresent() && configurationManager.getBooleanConfigValue(anonymousPathKey, true)) {
            String trackingId = Optional.ofNullable(StringUtils.trimToNull((String)session.getAttribute("GA_TRACKING_ID"))).orElseGet(() -> UUID.randomUUID().toString());
            Map<String, Object> model = new HashMap<>();
            model.put("clientId", trackingId);
            model.put("account", id.get());
            script = templateManager.renderTemplate(ev, TemplateResource.GOOGLE_ANALYTICS, model, Locale.ENGLISH);
        } else {
            script = id.map(x -> String.format(GOOGLE_ANALYTICS_SCRIPT, x)).orElse(EMPTY);
        }
        response.getWriter().write(script);
    }
}
