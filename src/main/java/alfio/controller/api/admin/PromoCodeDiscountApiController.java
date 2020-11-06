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
import alfio.manager.PromoCodeRequestManager;
import alfio.model.PromoCodeDiscount;
import alfio.model.metadata.AlfioMetadata;
import alfio.model.modification.PromoCodeDiscountModification;
import alfio.model.modification.PromoCodeDiscountWithFormattedTimeAndAmount;
import alfio.model.modification.PromotionalEmailModification;
import alfio.repository.EventRepository;
import alfio.repository.PromoCodeDiscountRepository;
import alfio.util.ClockProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static alfio.model.PromoCodeDiscount.categoriesOrNull;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
public class PromoCodeDiscountApiController {

    private final EventRepository eventRepository;
    private final PromoCodeDiscountRepository promoCodeRepository;
    private final EventManager eventManager;
    private final PromoCodeRequestManager promoCodeRequestManager;
    private final ClockProvider clockProvider;

    @PostMapping("/promo-code")
    public void addPromoCode(@RequestBody PromoCodeDiscountModification promoCode) {
        Integer eventId = promoCode.getEventId();
        Integer organizationId = promoCode.getOrganizationId();
        ZoneId zoneId = zoneIdFromEventId(eventId, promoCode.getUtcOffset());
        
        int discount = eventId != null ? promoCode.getDiscountValue(eventRepository.getEventCurrencyCode(eventId)) : promoCode.getDiscountAmount().intValue();

        var mData = AlfioMetadata.empty();
        if (promoCode.getAlfioMetadata() != null
            && promoCode.getAlfioMetadata().getTags() != null
            && promoCode.getAlfioMetadata().getTags().size() > 0
        ) {
            mData = new AlfioMetadata(promoCode.getAlfioMetadata().getTags(), null, Map.of(), List.of(), Map.of());
        }

        eventManager.addPromoCode(promoCode.getPromoCode(), eventId, organizationId, promoCode.getStart().toZonedDateTime(zoneId),
            promoCode.getEnd().toZonedDateTime(zoneId), discount, promoCode.getDiscountType(), promoCode.getCategories(), promoCode.getMaxUsage(),
            promoCode.getDescription(), promoCode.getEmailReference(), promoCode.getCodeType(), promoCode.getHiddenCategoryId(), mData);

        if (promoCode.isSendEmailToRecipient()){
            var pCode = promoCodeRepository.getPromoCodeRaw(promoCode.getPromoCode());
            if (pCode.isPresent()){
                promoCodeRequestManager.sendEMail(pCode.get().getId());
            }
        }
    }

    @PostMapping("/promo-code/{promoCodeId}")
    public void updatePromoCode(@PathVariable("promoCodeId") int promoCodeId, @RequestBody PromoCodeDiscountModification promoCode) {
        PromoCodeDiscount pcd = promoCodeRepository.findById(promoCodeId);
        ZoneId zoneId = zoneIdFromEventId(pcd.getEventId(), promoCode.getUtcOffset());
        eventManager.updatePromoCodeWithMetaData(promoCodeId, promoCode.getStart().toZonedDateTime(zoneId),
            promoCode.getEnd().toZonedDateTime(zoneId), promoCode.getMaxUsage(), promoCode.getCategories(),
            promoCode.getDescription(), promoCode.getEmailReference(), promoCode.getHiddenCategoryId(), promoCode.getAlfioMetadata());
        if (promoCode.isSendEmailToRecipient()){
            promoCodeRequestManager.sendEMail(pcd.getId());
        }
    }

    private ZoneId zoneIdFromEventId(Integer eventId, Integer utcOffset) {
        if(eventId != null) {
            return eventRepository.getZoneIdByEventId(eventId);
        } else {
            return ZoneId.ofOffset("UTC", ZoneOffset.ofTotalSeconds(utcOffset != null ? utcOffset : 0));
        }
    }

    @GetMapping("/events/{eventId}/promo-code")
    public List<PromoCodeDiscountWithFormattedTimeAndAmount> listPromoCodeInEvent(@PathVariable("eventId") int eventId) {
        return eventManager.findPromoCodesInEvent(eventId);
    }

    @GetMapping("/organization/{organizationId}/promo-code")
    public List<PromoCodeDiscountWithFormattedTimeAndAmount> listPromoCodeInOrganization(@PathVariable("organizationId") int organizationId) {
        return eventManager.findPromoCodesInOrganization(organizationId);
    }
    
    @DeleteMapping("/promo-code/{promoCodeId}")
    public void removePromoCode(@PathVariable("promoCodeId") int promoCodeId) {
        eventManager.deletePromoCode(promoCodeId);
    }
    
    @PostMapping("/promo-code/{promoCodeId}/disable")
    public void disablePromoCode(@PathVariable("promoCodeId") int promoCodeId) {
        promoCodeRepository.updateEventPromoCodeEnd(promoCodeId, ZonedDateTime.now(clockProvider.getClock()));
    }

    @PostMapping("/promo-code/{promoCodeId}/send-email")
    public boolean sendEmailPromoCode(@PathVariable("promoCodeId") int promoCodeId) {
        return promoCodeRequestManager.sendEMail(promoCodeId);
    }

    @PostMapping("/promo-code/{organizationId}/send-promotional-email")
    public boolean sendPromotionalEmail(@PathVariable("organizationId") int organizationId, @RequestBody PromotionalEmailModification promotionalEmail) {
        for (var recipient: promotionalEmail.getRecipients()) {
            promoCodeRequestManager.sendPromotionalEmail(recipient, promotionalEmail.getSubject(), promotionalEmail.getMessage(), organizationId);
        }
        return true;
    }
    
    @GetMapping("/promo-code/{promoCodeId}/count-use")
    public int countPromoCodeUse(@PathVariable("promoCodeId") int promoCodeId) {
        Optional<PromoCodeDiscount> code = promoCodeRepository.findOptionalById(promoCodeId);
        if(code.isEmpty()) {
            return 0;
        }
        return promoCodeRepository.countConfirmedPromoCode(promoCodeId, categoriesOrNull(code.get()), null, categoriesOrNull(code.get()) != null ? "X" : null);
    }
}
