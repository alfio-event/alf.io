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
import alfio.model.Event;
import alfio.model.PromoCodeDiscount;
import alfio.model.PromoCodeDiscount.DiscountType;
import alfio.model.modification.PromoCodeDiscountModification;
import alfio.model.modification.PromoCodeDiscountWithFormattedTime;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.springframework.web.bind.annotation.RequestMethod.*;

@RestController
@RequestMapping("/admin/api")
public class PromoCodeDiscountApiController {

    private final EventRepository eventRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final EventManager eventManager;

    @Autowired
    public PromoCodeDiscountApiController(EventRepository eventRepository, PromoCodeDiscountRepository promoCodeRepository, EventManager eventManager) {
        this.eventRepository = eventRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.eventManager = eventManager;
    }

    @RequestMapping(value = "/events/{eventId}/promo-code", method = POST)
    public void addPromoCode(@PathVariable("eventId") int eventId, @RequestBody PromoCodeDiscountModification promoCode) {
        Event event = eventRepository.findById(eventId);
        ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
        
        int discount = promoCode.getDiscountType() == DiscountType.FIXED_AMOUNT ? promoCode.getDiscountInCents() : promoCode.getDiscountAsPercent();
        
        eventManager.addPromoCode(promoCode.getPromoCode(), eventId, promoCode.getStart().toZonedDateTime(zoneId), 
                promoCode.getEnd().toZonedDateTime(zoneId), discount, promoCode.getDiscountType(), promoCode.getCategories());
    }

    @RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}", method = POST)
    public void updatePromocode(@PathVariable("eventId") int eventId, @PathVariable("promoCodeName") String promoCodeName, @RequestBody PromoCodeDiscountModification promoCode) {
        Event event = eventRepository.findById(eventId);
        ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
        eventManager.updatePromoCode(promoCodeName, eventId, promoCode.getStart().toZonedDateTime(zoneId), promoCode.getEnd().toZonedDateTime(zoneId));
    }

    @RequestMapping(value = "/events/{eventId}/promo-code", method = GET)
    public List<PromoCodeDiscountWithFormattedTime> listPromoCodeInEvent(@PathVariable("eventId") int eventId) {
        return eventManager.findPromoCodesInEvent(eventId);
    }
    
    @RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}", method = DELETE)
    public void removePromoCode(@PathVariable("eventId") int eventId, @PathVariable("promoCodeName") String promoCodeName) {
        PromoCodeDiscount promoCode = promoCodeRepository.findPromoCodeInEvent(eventId, promoCodeName);
        eventManager.deletePromoCode(promoCode.getId());
    }
    
    @RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}/disable", method = POST)
    public void disablePromoCode(@PathVariable("eventId") int eventId, @PathVariable("promoCodeName") String promoCodeName) {
        promoCodeRepository.updateEnd(eventId, promoCodeName, ZonedDateTime.now());
    }
    
    @RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}/count-use", method = GET)
    public int countPromoCodeUse(@PathVariable("eventId") int eventId, @PathVariable("promoCodeName") String promoCodeName) {
        return promoCodeRepository.countAppliedPromoCode(eventId, promoCodeName);
    }
}
