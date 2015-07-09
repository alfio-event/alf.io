package alfio.controller.api;

import alfio.controller.form.WaitingQueueSubscriptionForm;
import alfio.manager.WaitingQueueManager;
import alfio.model.Event;
import alfio.repository.EventRepository;
import alfio.util.TemplateManager;
import alfio.util.ValidationResult;
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
            result.put("validationResult", ValidationResult.failed(new ValidationResult.ValidationError("shortName", "error.shortName")));
            return result;
        }

        ValidationResult validationResult = Validator.validateWaitingQueueSubscription(subscription, bindingResult);
        if(validationResult.isSuccess()) {
            model.addAttribute("error", !waitingQueueManager.subscribe(optional.get(), subscription.getFullName(), subscription.getEmail(), subscription.getUserLanguage()));
            result.put("partial", templateManager.renderServletContextResource("/WEB-INF/templates/event/waiting-queue-subscription-result.ms", model.asMap(), request, HTML));
        }
        result.put("validationResult", validationResult);
        return result;
    }
}
