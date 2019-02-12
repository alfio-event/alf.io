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

import alfio.controller.TicketController;
import alfio.controller.api.support.TicketHelper;
import alfio.controller.api.v2.user.model.TicketInfo;
import alfio.controller.api.v2.user.model.ValidatedResponse;
import alfio.controller.form.UpdateTicketOwnerForm;
import alfio.manager.TicketReservationManager;
import alfio.repository.TicketCategoryRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v2/public/")
public class TicketApiV2Controller {


    private final TicketController ticketController;
    private final TicketHelper ticketHelper;
    private final TicketReservationManager ticketReservationManager;
    private final TicketCategoryRepository ticketCategoryRepository;


    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}/code.png")
    public void showQrCode(@PathVariable("eventName") String eventName,
                           @PathVariable("ticketIdentifier") String ticketIdentifier, HttpServletResponse response) throws IOException {
        ticketController.generateTicketCode(eventName, ticketIdentifier, response);
    }

    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}/download-ticket")
    public void generateTicketPdf(@PathVariable("eventName") String eventName,
                                  @PathVariable("ticketIdentifier") String ticketIdentifier,
                                  HttpServletRequest request, HttpServletResponse response) throws IOException {
        ticketController.generateTicketPdf(eventName, ticketIdentifier, request, response);
    }

    @PostMapping("/event/{eventName}/ticket/{ticketIdentifier}/send-ticket-by-email")
    public ResponseEntity<Boolean> sendTicketByEmail(@PathVariable("eventName") String eventName,
                                    @PathVariable("ticketIdentifier") String ticketIdentifier,
                                    HttpServletRequest request) {
        var res = ticketController.sendTicketByEmail(eventName, ticketIdentifier, request);
        return "OK".equals(res) ? ResponseEntity.ok(true) : ResponseEntity.notFound().build();
    }


    @GetMapping("/event/{eventName}/ticket/{ticketIdentifier}")
    public ResponseEntity<TicketInfo> getTicketInfo(@PathVariable("eventName") String eventName,
                                                    @PathVariable("ticketIdentifier") String ticketIdentifier) {

        //TODO: cleanup, we load useless data here!

        var oData = ticketReservationManager.fetchCompleteAndAssigned(eventName, ticketIdentifier);
        if(oData.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var data = oData.get();


        var ticketCategory = ticketCategoryRepository.getByIdAndActive(data.getRight().getCategoryId(), data.getLeft().getId());

        var ticket = data.getRight();

        var ticketInfo = new TicketInfo(ticket.getFullName(), ticket.getEmail(), ticket.getUuid(),
            ticketCategory.getName(),
            data.getMiddle().getFullName(),
            ticketReservationManager.getShortReservationID(data.getLeft(), data.getMiddle()));

        return ResponseEntity.ok(ticketInfo);
    }

    @PutMapping("/event/{eventName}/ticket/{ticketIdentifier}")
    public ValidatedResponse<Boolean> updateTicketInfo(@PathVariable("eventName") String eventName,
                                              @PathVariable("ticketIdentifier") String ticketIdentifier,
                                              @RequestParam(value = "single-ticket", required = false, defaultValue = "false") boolean singleTicket,
                                              @RequestBody UpdateTicketOwnerForm updateTicketOwner,
                                              BindingResult bindingResult,
                                              HttpServletRequest request,
                                              Authentication authentication) {

        Optional<UserDetails> userDetails = Optional.ofNullable(authentication)
            .map(Authentication::getPrincipal)
            .filter(UserDetails.class::isInstance)
            .map(UserDetails.class::cast);

        var assignmentResult = ticketHelper.assignTicket(eventName,
            ticketIdentifier,
            updateTicketOwner,
            Optional.of(bindingResult),
            request, t -> { },
            userDetails, false);

        return assignmentResult.map(r -> new ValidatedResponse<>(r.getLeft(), r.getLeft().isSuccess())).orElseThrow(IllegalStateException::new);
    }

}
