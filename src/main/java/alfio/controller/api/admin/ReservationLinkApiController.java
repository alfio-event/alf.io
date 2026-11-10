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

import alfio.manager.AccessService;
import alfio.manager.ReservationLinkManager;
import alfio.model.ReservationLink;
import alfio.repository.TicketCategoryRepository;
import alfio.util.ClockProvider;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin/api/events/{eventName}/categories/{categoryId}/reservation-links")
@AllArgsConstructor
public class ReservationLinkApiController {

    private final ReservationLinkManager reservationLinkManager;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AccessService accessService;
    private final ClockProvider clockProvider;

    public record CreateRequest(int quantity, ZonedDateTime expiresAt) {}

    @GetMapping
    public ResponseEntity<List<ReservationLink>> list(@PathVariable String eventName,
                                                      @PathVariable int categoryId,
                                                      Principal principal) {
        accessService.checkCategoryOwnership(principal, eventName, categoryId);
        return ResponseEntity.ok(reservationLinkManager.findByCategoryId(categoryId));
    }

    @PostMapping
    public ResponseEntity<ReservationLink> create(@PathVariable String eventName,
                                                  @PathVariable int categoryId,
                                                  @RequestBody CreateRequest req,
                                                  Principal principal) {
        var eventAndOrg = accessService.checkCategoryOwnership(principal, eventName, categoryId);
        var category = ticketCategoryRepository.getById(categoryId);
        if (category == null || category.isAccessRestricted()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.quantity() < 1 || req.expiresAt() == null
                || !req.expiresAt().isAfter(ZonedDateTime.now(clockProvider.getClock()))) {
            return ResponseEntity.badRequest().build();
        }
        var link = reservationLinkManager.generate(eventAndOrg.getId(), categoryId, req.quantity(), req.expiresAt());
        return ResponseEntity.ok(link);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String eventName,
                                       @PathVariable int categoryId,
                                       @PathVariable int id,
                                       Principal principal) {
        accessService.checkCategoryOwnership(principal, eventName, categoryId);
        reservationLinkManager.revoke(id);
        return ResponseEntity.ok().build();
    }
}
