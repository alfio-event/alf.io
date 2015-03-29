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

import alfio.model.modification.MessageModification;
import alfio.util.TemplateManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/events/{eventName}/messages")
public class MessagesApiController {

    private final TemplateManager templateManager;

    @Autowired
    public MessagesApiController(TemplateManager templateManager) {
        this.templateManager = templateManager;
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleException(IllegalStateException ex) {
        return Optional.ofNullable(ex.getCause()).map(Throwable::getMessage).orElseGet(ex::getMessage);
    }

    @RequestMapping(value= "/preview", method = RequestMethod.POST)
    public List<MessageModification> preview(@PathVariable("eventName") String eventName, @RequestBody List<MessageModification> messageModifications, Model model) {
        model.addAttribute("eventName", eventName);
        model.addAttribute("fullName", "First Last");
        model.addAttribute("organizationName", "My organization");
        model.addAttribute("organizationEmail", "test@test.tld");
        model.addAttribute("reservationURL", "https://my-instance/reservations/abcd");
        model.addAttribute("reservationID", "ABCD");
        return messageModifications.stream()
                .map(m -> MessageModification.preview(m, renderResource(m.getSubject(), model, m.getLocale(), templateManager), renderResource(m.getText(), model, m.getLocale(), templateManager)))
                .collect(Collectors.toList());
    }

    private static String renderResource(String template, Model model, Locale locale, TemplateManager templateManager) {
        return templateManager.renderString(template, model.asMap(), locale, TemplateManager.TemplateOutput.TEXT);
    }




}
