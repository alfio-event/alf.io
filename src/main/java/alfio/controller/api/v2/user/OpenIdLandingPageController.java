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
package alfio.controller.api.v2.user;

import alfio.model.PurchaseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

@Controller
public class OpenIdLandingPageController {

    private static final Logger log = LoggerFactory.getLogger(OpenIdLandingPageController.class);

    @GetMapping("/openid/{purchaseContextType}/{publicIdentifier}/reservation/{reservationId}")
    public String redirectToReservation(@PathVariable PurchaseContext.PurchaseContextType purchaseContextType,
                                        @PathVariable String publicIdentifier,
                                        @PathVariable String reservationId,
                                        Principal principal) {
        if (log.isTraceEnabled()) {
            log.trace("redirecting to reservation. Principal exists: {}", principal != null);
        }
        return "redirect:/" + purchaseContextType + "/" + publicIdentifier + "/reservation/" + reservationId;
    }
}
