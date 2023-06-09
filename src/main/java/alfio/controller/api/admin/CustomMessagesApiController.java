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

import alfio.manager.AccessService;
import alfio.manager.support.CustomMessageManager;
import alfio.model.modification.MessageModification;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/api/events/{eventName}/messages")
@Log4j2
public class CustomMessagesApiController {

    private final CustomMessageManager customMessageManager;
    private final AccessService accessService;

    @Autowired
    public CustomMessagesApiController(CustomMessageManager customMessageManager,
                                       AccessService accessService) {
        this.customMessageManager = customMessageManager;
        this.accessService = accessService;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleException(IllegalStateException ex) {
        log.warn("Exception in CustomMessagesApiController", ex);
        return Optional.ofNullable(ex.getCause()).map(Throwable::getMessage).orElseGet(ex::getMessage);
    }

    @PostMapping("/preview")
    public Map<String, Object> preview(@PathVariable("eventName") String eventName,
                                       @RequestParam(required = false, value = "categoryId") Integer categoryId,
                                       @RequestBody List<MessageModification> messageModifications, Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        return customMessageManager.generatePreview(eventName, Optional.ofNullable(categoryId), messageModifications, principal.getName());
    }

    @PostMapping("/send")
    public void send(@PathVariable("eventName") String eventName,
                    @RequestParam(required = false, value = "categoryId") Integer categoryId,
                    @RequestBody List<MessageModification> messageModifications,
                    Principal principal) {
        accessService.checkEventOwnership(principal, eventName);
        customMessageManager.sendMessages(eventName, Optional.ofNullable(categoryId), messageModifications, principal.getName());
    }

}
