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

import alfio.manager.EventManager;
import alfio.model.AdditionalService;
import alfio.model.Event;
import alfio.model.PriceContainer;
import alfio.model.modification.EventModification;
import alfio.model.result.ValidationResult;
import alfio.repository.AdditionalServiceRepository;
import alfio.repository.AdditionalServiceTextRepository;
import alfio.repository.EventRepository;
import alfio.util.MonetaryUtil;
import alfio.util.Validator;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.util.OptionalWrapper.optionally;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
@Log4j2
public class AdditionalServiceApiController {

    private final EventRepository eventRepository;
    private final AdditionalServiceRepository additionalServiceRepository;
    private final AdditionalServiceTextRepository additionalServiceTextRepository;
    private final EventManager eventManager;


    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<String> handleBadRequest(Exception e) {
        log.warn("bad input detected", e);
        return new ResponseEntity<>("bad input parameters", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<String> handleError(Exception e) {
        log.error("error", e);
        return new ResponseEntity<>("internal server error", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RequestMapping(value = "/event/{eventId}/additional-services", method = RequestMethod.GET)
    public List<EventModification.AdditionalService> loadAll(@PathVariable("eventId") int eventId) {
        return eventRepository.findOptionalById(eventId)
            .map(event -> additionalServiceRepository.loadAllForEvent(eventId)
                            .stream()
                            .map(as -> EventModification.AdditionalService.from(as)//.withAdditionalFields() TODO to be implemented
                                            .withText(additionalServiceTextRepository.findAllByAdditionalServiceId(as.getId()))
                                            .withZoneId(event.getZoneId())
                                            .withPriceContainer(buildPriceContainer(event, as)).build())
                            .collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }

    @GetMapping("/event/{eventId}/additional-services/count")
    public Map<Integer, Integer> countUse(@PathVariable("eventId") int eventId) {
        return additionalServiceRepository.getCount(eventId);
    }

    @RequestMapping(value = "/event/{eventId}/additional-services/{additionalServiceId}", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<EventModification.AdditionalService> update(@PathVariable("eventId") int eventId, @PathVariable("additionalServiceId") int additionalServiceId, @RequestBody EventModification.AdditionalService additionalService, BindingResult bindingResult) {
        ValidationResult validationResult = Validator.validateAdditionalService(additionalService, bindingResult);
        Validate.isTrue(validationResult.isSuccess(), "validation failed");
        Validate.isTrue(additionalServiceId == additionalService.getId(), "wrong input");
        return eventRepository.findOptionalById(eventId)
            .map(event -> {
                int result = additionalServiceRepository.update(additionalServiceId, additionalService.isFixPrice(),
                    additionalService.getOrdinal(), additionalService.getAvailableQuantity(), additionalService.getMaxQtyPerOrder(), additionalService.getInception().toZonedDateTime(event.getZoneId()),
                    additionalService.getExpiration().toZonedDateTime(event.getZoneId()), additionalService.getVat(), additionalService.getVatType(), Optional.ofNullable(additionalService.getPrice()).map(MonetaryUtil::unitToCents).orElse(0));
                Validate.isTrue(result <= 1, "too many records updated");
                Stream.concat(additionalService.getTitle().stream(), additionalService.getDescription().stream()).
                    forEach(t -> {
                        if(t.getId() != null) {
                            additionalServiceTextRepository.update(t.getId(), t.getLocale(), t.getType(), t.getValue());
                        } else {
                            additionalServiceTextRepository.insert(additionalService.getId(), t.getLocale(), t.getType(), t.getValue());
                        }
                    });
                return ResponseEntity.ok(additionalService);
            }).orElseThrow(IllegalArgumentException::new);
    }

    @RequestMapping(value = "/event/{eventId}/additional-services", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<EventModification.AdditionalService> insert(@PathVariable("eventId") int eventId, @RequestBody EventModification.AdditionalService additionalService, BindingResult bindingResult) {
        ValidationResult validationResult = Validator.validateAdditionalService(additionalService, bindingResult);
        Validate.isTrue(validationResult.isSuccess(), "validation failed");
        return eventRepository.findOptionalById(eventId)
            .map(event -> ResponseEntity.ok(eventManager.insertAdditionalService(event, additionalService)))
            .orElseThrow(IllegalArgumentException::new);
    }

    @RequestMapping(value = "/event/{eventId}/additional-services/{additionalServiceId}", method = RequestMethod.DELETE)
    @Transactional
    public ResponseEntity<String> remove(@PathVariable("eventId") int eventId, @PathVariable("additionalServiceId") int additionalServiceId, Principal principal) {
        return eventRepository.findOptionalById(eventId)
            .map(event -> additionalServiceRepository.getOptionalById(additionalServiceId, eventId)
                .map(as -> {
                    log.debug("{} is deleting additional service #{}", principal.getName(), additionalServiceId);
                    int deletedTexts = additionalServiceTextRepository.deleteAdditionalServiceTexts(additionalServiceId);
                    log.debug("deleted {} texts", deletedTexts);
                    //TODO add configuration fields and values
                    additionalServiceRepository.delete(additionalServiceId, eventId);
                    log.debug("additional service #{} successfully deleted", additionalServiceId);
                    return ResponseEntity.ok("OK");
                })
                .orElseGet(() -> new ResponseEntity<>("additional service not found", HttpStatus.NOT_FOUND)))
            .orElseGet(() -> new ResponseEntity<>("event not found", HttpStatus.NOT_FOUND));
    }

    @RequestMapping(value = "/additional-services/validate", method = RequestMethod.POST)
    public ValidationResult checkAdditionalService(@RequestBody EventModification.AdditionalService additionalService, BindingResult bindingResult) {
        return Validator.validateAdditionalService(additionalService, bindingResult);
    }

    private static PriceContainer buildPriceContainer(final Event event, final AdditionalService as) {
        return new PriceContainer() {
            @Override
            public int getSrcPriceCts() {
                return as.isFixPrice() ? as.getSrcPriceCts() : 0;
            }

            @Override
            public String getCurrencyCode() {
                return event.getCurrency();
            }

            @Override
            public Optional<BigDecimal> getOptionalVatPercentage() {
                return getVatStatus() == VatStatus.NONE ? Optional.empty() : Optional.of(event.getVat());
            }

            @Override
            public VatStatus getVatStatus() {
                return AdditionalService.getVatStatus(as.getVatType(), event.getVatStatus());
            }
        };
    }
}
