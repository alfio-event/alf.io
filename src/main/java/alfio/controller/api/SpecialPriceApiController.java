package alfio.controller.api;

import alfio.manager.SpecialPriceManager;
import alfio.model.modification.SendCodeModification;
import com.opencsv.CSVReader;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
                                                          @RequestParam("file") MultipartFile file,
                                                          Principal principal) throws IOException {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        try(InputStreamReader isr = new InputStreamReader(file.getInputStream())) {
            CSVReader reader = new CSVReader(isr);
            Set<SendCodeModification> content = reader.readAll().stream()
                    .map(line -> {
                        Validate.isTrue(line.length >= 4);
                        return new SendCodeModification(StringUtils.trimToNull(line[0]), line[1], line[2], line[3]);
                    })
                    .collect(Collectors.toSet());
            return specialPriceManager.linkAssigneeToCode(content, eventName, categoryId, principal.getName());
        }
    }

    @RequestMapping("/events/{eventName}/categories/{categoryId}/send-codes")
    public boolean sendCodes(@PathVariable("eventName") String eventName,
                             @PathVariable("categoryId") int categoryId,
                             @RequestBody Set<SendCodeModification> codes,
                             Principal principal) throws IOException {

        Validate.isTrue(StringUtils.isNotEmpty(eventName));
        Objects.requireNonNull(codes);
        Validate.isTrue(!codes.isEmpty(), "Collection of codes cannot be empty");
        specialPriceManager.sendCodeToAssignee(codes, eventName, categoryId, principal.getName());
        return true;
    }

}
