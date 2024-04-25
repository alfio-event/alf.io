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
import alfio.manager.SpecialPriceManager;
import alfio.model.SpecialPrice;
import alfio.model.modification.SendCodeModification;
import alfio.model.modification.UploadBase64FileModification;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Principal;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/admin/api")
public class SpecialPriceApiController {

    private static final Logger log = LoggerFactory.getLogger(SpecialPriceApiController.class);
    private final SpecialPriceManager specialPriceManager;
    private final AccessService accessService;

    public SpecialPriceApiController(SpecialPriceManager specialPriceManager,
                                     AccessService accessService) {
        this.specialPriceManager = specialPriceManager;
        this.accessService = accessService;
    }

    @ExceptionHandler
    @ResponseBody
    public ResponseEntity<String> handleExceptions(Exception e) {
        log.error("Unexpected exception in SpecialPriceApiController", e);
        if(!(e instanceof IllegalArgumentException)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    @PostMapping("/events/{eventName}/categories/{categoryId}/link-codes")
    public ResponseEntity<List<SendCodeModification>> linkAssigneeToCodes(@PathVariable String eventName,
                                                                         @PathVariable int categoryId,
                                                                         @RequestBody UploadBase64FileModification file,
                                                                         Principal principal) throws IOException {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        accessService.checkCategoryOwnership(principal, eventName, categoryId);
        try(InputStreamReader isr = new InputStreamReader(file.getInputStream(), UTF_8)) {
            MappingIterator<SendCodeModification> iterator = new CsvMapper().readerFor(SendCodeModification.class)
                .with(CsvSchema.emptySchema().withoutHeader())
                .readValues(isr);
            return ResponseEntity.ok(specialPriceManager.linkAssigneeToCode(iterator.readAll(), eventName, categoryId, principal.getName()));
        }
    }

    @PostMapping("/events/{eventName}/categories/{categoryId}/send-codes")
    public boolean sendCodes(@PathVariable String eventName,
                             @PathVariable int categoryId,
                             @RequestBody List<SendCodeModification> codes,
                             Principal principal) {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        Objects.requireNonNull(codes);
        Validate.isTrue(!codes.isEmpty(), "Collection of codes cannot be empty");
        specialPriceManager.sendCodeToAssignee(codes, eventName, categoryId, principal.getName());
        return true;
    }

    @GetMapping("/events/{eventName}/categories/{categoryId}/sent-codes")
    public List<SpecialPrice> loadSentCodes(@PathVariable String eventName,
                                            @PathVariable int categoryId,
                                            Principal principal) {
        return specialPriceManager.loadSentCodes(eventName, categoryId, principal.getName());
    }

    @DeleteMapping("/events/{eventName}/categories/{categoryId}/codes/{codeId}/recipient")
    public boolean clearRecipientData(@PathVariable String eventName,
                                      @PathVariable int categoryId,
                                      @PathVariable int codeId,
                                      Principal principal) {
        return specialPriceManager.clearRecipientData(eventName, categoryId, codeId, principal.getName());
    }

}
