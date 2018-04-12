package alfio.controller.api.v1.admin;

import alfio.controller.api.admin.EventApiController;
import alfio.manager.EventManager;
import alfio.manager.EventNameManager;
import alfio.manager.FileDownloadManager;
import alfio.manager.FileUploadManager;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.modification.EventModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import alfio.model.user.Organization;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;


@RestController
@RequestMapping("/api/v1/admin/event")
@AllArgsConstructor
public class EventApiV1Controller {

    private final EventManager eventManager;
    private final EventNameManager eventNameManager;
    private final FileUploadManager fileUploadManager;
    private final FileDownloadManager fileDownloadManager;
    private final UserManager userManager;

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody EventCreationRequest request, Principal user) throws IOException {


        String imageRef = fetchImage(request.getImageUrl());

        Result<String> result =  new Result.Builder<String>()
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getTitle()),ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getSlug()) && eventNameManager.isUnique(request.getSlug()),ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getWebsiteUrl()),ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getTermsAndConditionsUrl()),ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getImageUrl()),ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> StringUtils.isNotBlank(request.getTimezone()),ErrorCode.EventError.NOT_FOUND)
            //TODO all location validation
            //TODO language validation, for all the description the same languages
            .build(() -> insertEvent(request, user, imageRef).map((e) -> e.getShortName()).get());

        if(result.isSuccess())
           return ResponseEntity.ok(result.getData());
        else
            return ResponseEntity.badRequest().build();

    }

    private Optional<Event> insertEvent(EventCreationRequest request, Principal user, String imageRef) {
        Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
        EventModification em = request.toEventModification(organization,eventNameManager::generateShortName,imageRef);

        ValidationResult vr = EventApiController.validateEvent(em,new MapBindingResult(new HashMap<>(),""));
        eventManager.createEvent(em);
        Optional<Event> event = eventManager.getOptionalByName(em.getShortName(),user.getName());

        event.ifPresent((e) ->
            Optional.ofNullable(request.getTickets().getPromoCodes()).ifPresent((promoCodes) ->
                promoCodes.forEach((pc) -> //TODO add ref to categories
                    eventManager.addPromoCode(
                        pc.getName(),
                        e.getId(),
                        organization.getId(),
                        ZonedDateTime.of(pc.getValidFrom(),e.getZoneId()),
                        ZonedDateTime.of(pc.getValidTo(),e.getZoneId()),
                        pc.getDiscount(),
                        pc.getDiscountType(),
                        Collections.emptyList()
                    )
                )
            )
        );
        return event;
    }

    private String fetchImage(String url) throws IOException {
        FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
        return fileUploadManager.insertFile(file.toUploadBase64FileModification());
    }






}
