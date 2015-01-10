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
package alfio.controller.api;

import static org.springframework.web.bind.annotation.RequestMethod.*;

import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import alfio.manager.EventManager;
import alfio.model.Event;
import alfio.model.PromoCode;
import alfio.model.modification.PromoCodeModification;
import alfio.model.modification.PromoCodeWithFormattedTime;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeRepository;

@RestController
@RequestMapping("/admin/api")
public class PromoCodeApiController {

	private final EventRepository eventRepository;
	private final PromoCodeRepository promoCodeRepository;
	private final EventManager eventManager;

	@Autowired
	public PromoCodeApiController(EventRepository eventRepository, PromoCodeRepository promoCodeRepository, EventManager eventManager) {
		this.eventRepository = eventRepository;
		this.promoCodeRepository = promoCodeRepository;
		this.eventManager = eventManager;
	}

	@RequestMapping(value = "/events/{eventId}/promo-code", method = POST)
	public void addPromoCode(@PathVariable("eventId") int eventId, @RequestBody PromoCodeModification promoCode) {
		Event event = eventRepository.findById(eventId);
		ZoneId zoneId = TimeZone.getTimeZone(event.getTimeZone()).toZoneId();
		
		eventManager.addPromoCode(promoCode.getPromoCode(), eventId, promoCode.getStart().toZonedDateTime(zoneId), 
				promoCode.getEnd().toZonedDateTime(zoneId), promoCode.getDiscountAmount(), promoCode.getDiscountType());
	}

	@RequestMapping(value = "/events/{eventId}/promo-code", method = GET)
	public List<PromoCodeWithFormattedTime> listPromoCodeInEvent(@PathVariable("eventId") int eventId) {
		return eventManager.findPromoCodesInEvent(eventId);
	}
	
	@RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}", method = DELETE)
	public void removePromoCode(@PathVariable("eventId") int eventId, @PathVariable("promoCodeName") String promoCodeName) {
		PromoCode promoCode = promoCodeRepository.findPromoCodeInEvent(eventId, promoCodeName);
		eventManager.deletePromoCode(promoCode.getId());
	}
	
	@RequestMapping(value = "/events/{eventId}/promo-code/{promoCodeName}", method = POST)
	public void updatePromoCode(@PathVariable("eventId") int eventId, @RequestBody PromoCodeModification promoCode) {
		//FIXME complete. Will be used to disable and/or change validity date
	}
}
