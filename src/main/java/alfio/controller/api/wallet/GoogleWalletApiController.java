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
package alfio.controller.api.wallet;

import alfio.manager.wallet.GoogleWalletManager;
import alfio.model.EventAndOrganizationId;
import alfio.model.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

@RestController
@RequestMapping("/api/wallet/event/{eventName}/v1")
@Log4j2
@RequiredArgsConstructor
public class GoogleWalletApiController {

    private final GoogleWalletManager walletManager;

    @GetMapping("/version/passes/{uuid}")
    public void walletPass(@PathVariable("eventName") String eventName,
                                 @PathVariable("uuid") String serialNumber,
                                 HttpServletResponse response) throws IOException {
        Optional<Pair<EventAndOrganizationId, Ticket>> validationResult = walletManager.validateTicket(eventName, serialNumber);
        if (validationResult.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } else {
            String walletUrl = walletManager.createAddToWalletUrl(validationResult.get().getRight(), validationResult.get().getLeft());
            response.sendRedirect(walletUrl);
        }
    }

}
