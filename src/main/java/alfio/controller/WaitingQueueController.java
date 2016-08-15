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

import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.manager.WaitingQueueManager;
import alfio.model.Event;
import alfio.repository.EventRepository;
import alfio.util.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WaitingQueueController {

    private final WaitingQueueManager waitingQueueManager;
    private final EventRepository eventRepository;

    @Autowired
    public WaitingQueueController(WaitingQueueManager waitingQueueManager,
                                  EventRepository eventRepository) {
        this.waitingQueueManager = waitingQueueManager;
        this.eventRepository = eventRepository;
    }

    @RequestMapping(value = "/event/{eventName}/waiting-queue/subscribe", method = RequestMethod.POST)
    public String subscribe(@ModelAttribute WaitingQueueSubscriptionForm subscription, BindingResult bindingResult, Model model, @PathVariable("eventName") String eventName, RedirectAttributes redirectAttributes) {
        Validator.validateWaitingQueueSubscription(subscription, bindingResult).ifSuccess(() -> {
            Event event = eventRepository.findOptionalByShortName(eventName).orElseThrow(IllegalArgumentException::new);
            if(waitingQueueManager.subscribe(event, subscription.getFullName(), subscription.getEmail(), subscription.getSelectedCategory(), subscription.getUserLanguage())) {
                redirectAttributes.addFlashAttribute("subscriptionComplete", true);
            } else {
                redirectAttributes.addFlashAttribute("subscriptionError", true);
            }
        });
        return "redirect:/event/"+eventName+"/";
    }

}
