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

import alfio.extension.ExtensionService;
import alfio.manager.*;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.*;
import alfio.model.ExtensionSupport.ExtensionMetadataValue;
import alfio.model.api.v1.admin.EventCreationRequest;
import alfio.model.group.Group;
import alfio.model.modification.EventModification;
import alfio.model.modification.LinkedGroupModification;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.Organization;
import alfio.repository.ExtensionRepository;
import alfio.util.Json;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static alfio.controller.api.admin.EventApiController.validateEvent;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;


@RestController
@RequestMapping("/api/v1/admin/event")
@AllArgsConstructor
@Log4j2
public class EventApiV1Controller {

    private final EventManager eventManager;
    private final EventNameManager eventNameManager;
    private final FileUploadManager fileUploadManager;
    private final FileDownloadManager fileDownloadManager;
    private final UserManager userManager;
    private final EventStatisticsManager eventStatisticsManager;
    private final GroupManager groupManager;
    private final ExtensionService extensionService;
    private final ExtensionRepository extensionRepository;
    private final ConfigurationManager configurationManager;

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<String> create(@RequestBody EventCreationRequest request, Principal user) {


        String imageRef = Optional.ofNullable(request.getImageUrl()).map(this::fetchImage).orElse(null);
        Organization organization = userManager.findUserOrganizations(user.getName()).get(0);
        AtomicReference<Errors> errorsContainer = new AtomicReference<>();
        Result<String> result =  new Result.Builder<String>()
            .checkPrecondition(() -> isNotBlank(request.getTitle()), ErrorCode.custom("invalid.title", "Invalid title"))
            .checkPrecondition(() -> isBlank(request.getSlug()) || eventNameManager.isUnique(request.getSlug()), ErrorCode.custom("invalid.slug", "Invalid slug"))
            .checkPrecondition(() -> isNotBlank(request.getWebsiteUrl()), ErrorCode.custom("invalid.websiteUrl", "Invalid Website URL"))
            .checkPrecondition(() -> isNotBlank(request.getTermsAndConditionsUrl()), ErrorCode.custom("invalid.tc", "Invalid Terms and Conditions"))
            .checkPrecondition(() -> isNotBlank(request.getImageUrl()), ErrorCode.custom("invalid.imageUrl", "Invalid Image URL"))
            .checkPrecondition(() -> isNotBlank(request.getTimezone()), ErrorCode.custom("invalid.timezone", "Invalid Timezone"))
            .checkPrecondition(() -> isNotBlank(imageRef), ErrorCode.custom("invalid.image", "Image is either missing or too big (max 200kb)"))
            .checkPrecondition(() -> {
                EventModification eventModification = request.toEventModification(organization, eventNameManager::generateShortName, imageRef);
                errorsContainer.set(new BeanPropertyBindingResult(eventModification, "event"));
                int descriptionMaxLength = configurationManager.getFor(ConfigurationKeys.DESCRIPTION_MAXLENGTH, ConfigurationLevel.system()).getValueAsIntOrDefault(4096);
                ValidationResult validationResult = validateEvent(eventModification, errorsContainer.get(), descriptionMaxLength);
                if(!validationResult.isSuccess()) {
                    log.warn("validation failed {}", validationResult.getValidationErrors());
                }
                return validationResult.isSuccess();
            }, ErrorCode.lazy(() -> toErrorCode(errorsContainer.get())))
            //TODO all location validation
            //TODO language validation, for all the description the same languages
            .build(() -> insertEvent(request, user, imageRef).map(Event::getShortName).orElseThrow(IllegalStateException::new));

        if(result.isSuccess()) {
            return ResponseEntity.ok(result.getData());
        } else {
            return ResponseEntity.badRequest().body(Json.toJson(result.getErrors()));
        }

    }

    private ErrorCode toErrorCode(Errors errors) {
        return errors.getFieldErrors().stream()
            .map(e -> ErrorCode.custom(e.getField(), e.getCode()))
            .findFirst()
            .orElse(ErrorCode.EventError.NOT_FOUND);
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
                eventManager.getOptionalEventAndOrganizationIdByName(slug,user.getName()).ifPresent( e -> eventManager.deleteEvent(e.getId(),user.getName()));
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
            .build(() -> updateEvent(slug, request, user, imageRef).map(Event::getShortName).get());

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


        if (em.getTicketCategories() != null && !em.getTicketCategories().isEmpty()) {
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
        eventManager.createEvent(em, user.getName());
        Optional<Event> event = eventManager.getOptionalByName(em.getShortName(),user.getName());

        event.ifPresent(e -> {
            Optional.ofNullable(request.getTickets().getPromoCodes()).ifPresent(promoCodes ->
                promoCodes.forEach(pc -> //TODO add ref to categories
                    eventManager.addPromoCode(
                        pc.getName(),
                        e.getId(),
                        organization.getId(),
                        ZonedDateTime.of(pc.getValidFrom(),e.getZoneId()),
                        ZonedDateTime.of(pc.getValidTo(),e.getZoneId()),
                        pc.getDiscount(),
                        pc.getDiscountType(),
                        Collections.emptyList(),
                        null,
                        null,
                        null,
                        PromoCodeDiscount.CodeType.DISCOUNT,
                        null
                    )
                )
            );
            //link categories to groups, if any
            request.getTickets().getCategories().stream()
                .filter(cr -> cr.getGroupLink() != null && cr.getGroupLink().getGroupId() != null)
                .map(cr -> Pair.of(cr, groupManager.findById(cr.getGroupLink().getGroupId(), organization.getId())))
                .forEach(link -> {
                    if(link.getRight().isPresent()) {
                        Group group = link.getRight().get();
                        EventCreationRequest.CategoryRequest categoryRequest = link.getLeft();
                        findCategoryByName(e, categoryRequest.getName()).ifPresent(category -> {
                            EventCreationRequest.GroupLinkRequest groupLinkRequest = categoryRequest.getGroupLink();
                            LinkedGroupModification modification = new LinkedGroupModification(null,
                                group.getId(),
                                e.getId(),
                                category.getId(),
                                groupLinkRequest.getType(),
                                groupLinkRequest.getMatchType(),
                                groupLinkRequest.getMaxAllocation());
                            groupManager.createLink(group.getId(), e.getId(), modification);
                        });
                    }
                });
            if(!CollectionUtils.isEmpty(request.getExtensionSettings())) {
                request.getExtensionSettings().stream()
                    .collect(Collectors.groupingBy(EventCreationRequest.ExtensionSetting::getExtensionId))
                    .forEach((id,settings) -> {
                        List<ExtensionSupport.ExtensionMetadataIdAndName> metadata = extensionService.getSingle(organization, e, id)
                            .map(es -> extensionRepository.findAllParametersForExtension(es.getId()))
                            .orElseGet(Collections::emptyList);

                        List<ExtensionMetadataValue> values = settings.stream()
                            .map(es -> Pair.of(es, metadata.stream().filter(mm -> mm.getName().equals(es.getKey())).findFirst()))
                            .filter(pair -> {
                                if (pair.getRight().isEmpty()) {
                                    log.warn("ignoring non-existent extension setting key {}", pair.getLeft().getKey());
                                }
                                return pair.getRight().isPresent();
                            })
                            .map(pair -> new ExtensionMetadataValue(pair.getRight().get().getId(), pair.getLeft().getValue()))
                            .collect(Collectors.toList());
                        extensionService.bulkUpdateEventSettings(organization, e, values);
                    });

            }

        });
        return event;
    }

    private String fetchImage(String url) {
        if(url != null) {
            FileDownloadManager.DownloadedFile file = fileDownloadManager.downloadFile(url);
            return file != null ? fileUploadManager.insertFile(file.toUploadBase64FileModification()) : null;
        } else {
            return null;
        }
    }






}
