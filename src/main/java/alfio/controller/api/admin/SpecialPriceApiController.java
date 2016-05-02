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

import alfio.manager.SpecialPriceManager;
import alfio.model.modification.SendCodeModification;
import alfio.model.modification.UploadBase64FileModification;
import com.opencsv.CSVReader;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api")
@Log4j2
public class SpecialPriceApiController {

    private final SpecialPriceManager specialPriceManager;

    @Autowired
    public SpecialPriceApiController(SpecialPriceManager specialPriceManager) {
        this.specialPriceManager = specialPriceManager;
    }

    @ExceptionHandler
    @ResponseBody
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleExceptions(Exception e) {
        if(!IllegalArgumentException.class.isInstance(e)) {
            log.error("Unexpected exception in SpecialPriceApiController", e);
        }
        return e.toString();
    }

    @RequestMapping("/events/{eventName}/categories/{categoryId}/link-codes")
    public List<SendCodeModification> linkAssigneeToCodes(@PathVariable("eventName") String eventName,
                                                          @PathVariable("categoryId") int categoryId,
                                                          @RequestBody UploadBase64FileModification file,
                                                          Principal principal) throws IOException {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        try(InputStreamReader isr = new InputStreamReader(file.getInputStream()); CSVReader reader = new CSVReader(isr)) {
            List<SendCodeModification> content = reader.readAll().stream()
                    .map(line -> {
                        Validate.isTrue(line.length >= 4);
                        return new SendCodeModification(StringUtils.trimToNull(line[0]), line[1], line[2], line[3]);
                    })
                    .collect(Collectors.toList());
            return specialPriceManager.linkAssigneeToCode(content, eventName, categoryId, principal.getName());
        }
    }

    @RequestMapping("/events/{eventName}/categories/{categoryId}/send-codes")
    public boolean sendCodes(@PathVariable("eventName") String eventName,
                             @PathVariable("categoryId") int categoryId,
                             @RequestBody List<SendCodeModification> codes,
                             Principal principal) throws IOException {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        Objects.requireNonNull(codes);
        Validate.isTrue(!codes.isEmpty(), "Collection of codes cannot be empty");
        specialPriceManager.sendCodeToAssignee(codes, eventName, categoryId, principal.getName());
        return true;
    }

}
