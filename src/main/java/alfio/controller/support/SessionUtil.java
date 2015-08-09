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
package alfio.controller.support;

import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

public final class SessionUtil {
    
    private static final String SPECIAL_PRICE_CODE_SESSION_ID = "SPECIAL_PRICE_CODE_SESSION_ID";
    private static final String SPECIAL_PRICE_CODE = "SPECIAL_PRICE_CODE";
    
    private static final String PROMOTIONAL_CODE_DISCOUNT = "PROMOTIONAL_CODE_DISCOUNT";

    private SessionUtil() {}

    public static void saveSpecialPriceCode(String specialPriceCode, HttpServletRequest request) {
        if(StringUtils.isNotEmpty(specialPriceCode)) {
            request.getSession().setAttribute(SPECIAL_PRICE_CODE_SESSION_ID, UUID.randomUUID().toString());
            request.getSession().setAttribute(SPECIAL_PRICE_CODE, specialPriceCode);
        }
    }
    
    public static void savePromotionCodeDiscount(String promoCodeDiscount, HttpServletRequest request) {
        request.getSession().setAttribute(PROMOTIONAL_CODE_DISCOUNT, promoCodeDiscount);
    }

    public static Optional<String> retrieveSpecialPriceCode(HttpServletRequest request) {
        return Optional.ofNullable((String)request.getSession().getAttribute(SPECIAL_PRICE_CODE));
    }
    
    public static Optional<String> retrievePromotionCodeDiscount(HttpServletRequest request) {
        return Optional.ofNullable((String) request.getSession().getAttribute(PROMOTIONAL_CODE_DISCOUNT));
    }

    public static Optional<String> retrieveSpecialPriceSessionId(HttpServletRequest request) {
        return Optional.ofNullable((String)request.getSession().getAttribute(SPECIAL_PRICE_CODE_SESSION_ID));
    }

    public static void removeSpecialPriceData(HttpServletRequest request) {
        request.getSession().removeAttribute(SPECIAL_PRICE_CODE_SESSION_ID);
        request.getSession().removeAttribute(SPECIAL_PRICE_CODE);
        request.getSession().removeAttribute(PROMOTIONAL_CODE_DISCOUNT);
    }

    public static void addToFlash(BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", bindingResult).addFlashAttribute("hasErrors", true);
    }
}
