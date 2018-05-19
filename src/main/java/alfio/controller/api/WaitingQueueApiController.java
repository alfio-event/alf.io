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
package alfio.controller.api;

import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.manager.WaitingQueueManager;
import alfio.model.Event;
import alfio.model.result.ValidationResult;
import alfio.repository.EventRepository;
import alfio.util.TemplateManager;
import alfio.util.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static alfio.util.TemplateManager.TemplateOutput.HTML;

@RestController
public class WaitingQueueApiController {

    private final EventRepository eventRepository;
    private final WaitingQueueManager waitingQueueManager;
    private final TemplateManager templateManager;

    @Autowired
    public WaitingQueueApiController(EventRepository eventRepository,
                                     WaitingQueueManager waitingQueueManager,
                                     TemplateManager templateManager) {
        this.eventRepository = eventRepository;
        this.waitingQueueManager = waitingQueueManager;
        this.templateManager = templateManager;
    }

    @RequestMapping(value = "/event/{eventName}/waiting-queue/subscribe", method = RequestMethod.POST, headers = "X-Requested-With=XMLHttpRequest")
    public Map<String, Object> subscribe(WaitingQueueSubscriptionForm subscription,
                                         BindingResult bindingResult,
                                         Model model,
                                         @PathVariable("eventName") String eventName,
                                         HttpServletRequest request) {

        Optional<Event> optional = eventRepository.findOptionalByShortName(eventName);
        Map<String, Object> result = new HashMap<>();
        if(!optional.isPresent()) {
            bindingResult.reject("");
            result.put("validationResult", ValidationResult.failed(new ValidationResult.ErrorDescriptor("shortName", "error.shortName")));
            return result;
        }

        Event event = optional.get();

        ValidationResult validationResult = Validator.validateWaitingQueueSubscription(subscription, bindingResult, event);
        if(validationResult.isSuccess()) {
            model.addAttribute("error", !waitingQueueManager.subscribe(event, subscription.toCustomerName(event), subscription.getEmail(), subscription.getSelectedCategory(), subscription.getUserLanguage()));
            result.put("partial", templateManager.renderServletContextResource("/WEB-INF/templates/event/waiting-queue-subscription-result.ms", event, model.asMap(), request, HTML));
        }
        result.put("validationResult", validationResult);
        return result;
    }
}
