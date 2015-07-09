package alfio.controller;

import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.manager.WaitingQueueManager;
import alfio.model.Event;
import alfio.repository.EventRepository;
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
        Event event = eventRepository.findOptionalByShortName(eventName).orElseThrow(IllegalArgumentException::new);
        if(waitingQueueManager.subscribe(event, subscription.getFullName(), subscription.getEmail(), subscription.getUserLanguage())) {
            redirectAttributes.addFlashAttribute("subscriptionComplete", true);
        } else {
            redirectAttributes.addFlashAttribute("subscriptionError", true);
        }
        return "redirect:/event/"+eventName+"/";
    }

}
