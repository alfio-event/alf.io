package alfio.controller.api.admin;

import alfio.model.modification.EventModification;
import alfio.util.ValidationResult;
import alfio.util.Validator;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/additional-services")
public class AdditionalServiceApiController {

    @RequestMapping(value = "/validate", method = RequestMethod.POST)
    public ValidationResult checkAdditionalService(@RequestBody EventModification.AdditionalService additionalService, BindingResult bindingResult) {
        return Validator.validateAdditionalService(additionalService, bindingResult);
    }
}
