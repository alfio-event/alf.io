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
package alfio.controller.api.v1.admin;

import alfio.controller.api.admin.EventApiController;
import alfio.manager.*;
import alfio.manager.user.UserManager;
import alfio.model.Event;
import alfio.model.EventWithAdditionalInfo;
import alfio.model.TicketCategory;
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
import org.springframework.web.bind.annotation.*;

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
    private final EventStatisticsManager eventStatisticsManager;

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody EventCreationRequest request, Principal user) {


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

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }

    }

    @GetMapping("/{slug}/stats")
    public ResponseEntity<EventWithAdditionalInfo> stats(@PathVariable("slug") String slug, Principal user) {
        Result<EventWithAdditionalInfo> result = new Result.Builder<EventWithAdditionalInfo>()
            .build(() -> eventStatisticsManager.getEventWithAdditionalInfo(slug,user.getName()));

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<String> delete(@PathVariable("slug") String slug, Principal user) {
        Result<String> result =  new Result.Builder<String>()
            .build(() -> {
                eventManager.getOptionalByName(slug,user.getName()).ifPresent( e -> eventManager.deleteEvent(e.getId(),user.getName()));
                return "Ok";
            });
        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }


    @PostMapping("/update/{slug}")
    public ResponseEntity<String> update(@PathVariable("slug") String slug, @RequestBody EventCreationRequest request, Principal user) {

        String imageRef = fetchImage(request.getImageUrl());

        Result<String> result =  new Result.Builder<String>()
            .build(() -> updateEvent(slug, request, user, imageRef).map((e) -> e.getShortName()).get());

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    private Optional<Event> updateEvent(String slug, EventCreationRequest request, Principal user, String imageRef) {
        Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
        EventWithAdditionalInfo original = eventStatisticsManager.getEventWithAdditionalInfo(slug,user.getName());

        Event event = original.getEvent();


        EventModification em = request.toEventModificationUpdate(original,organization,imageRef);

        eventManager.updateEventHeader(event, em, user.getName());
        eventManager.updateEventPrices(event, em, user.getName());


        if (em.getTicketCategories() != null && em.getTicketCategories().size() > 0) {
            em.getTicketCategories().forEach(c ->
                findCategoryByName(event, c.getName()).ifPresent(originalCategory ->
                    eventManager.updateCategory(originalCategory.getId(), event.getId(), c, user.getName())
                )
            );
        }


        // TODO discusson about promocode needed

        return eventManager.getOptionalByName(slug,user.getName());
    }

    private Optional<TicketCategory> findCategoryByName(Event event, String name) {
        List<TicketCategory> categories = eventManager.loadTicketCategories(event);

        return categories.stream().filter( oc -> oc.getName().equals(name)).findFirst();
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

    private String fetchImage(String url) {
        if(url != null) {
            FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
            return fileUploadManager.insertFile(file.toUploadBase64FileModification());
        } else {
            return null;
        }
    }






}
