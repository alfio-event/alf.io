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
import alfio.manager.PollManager;
import alfio.model.modification.PollModification;
import alfio.model.poll.Poll;
import alfio.model.poll.PollParticipant;
import alfio.model.poll.PollStatistics;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/{eventName}/poll")
@RequiredArgsConstructor
public class PollAdminApiController {

    private final PollManager pollManager;
    private final AccessService accessService;

    @GetMapping
    ResponseEntity<List<PollModification>> getAllForEvent(@PathVariable("eventName") String eventName,
                                                          Principal principal) {
        if(StringUtils.isEmpty(eventName)) {
            return ResponseEntity.badRequest().build();
        }
        accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.ok(pollManager.getAllForEvent(eventName).stream().map(PollModification::from).collect(Collectors.toList()));
    }

    @GetMapping("/{pollId}")
    ResponseEntity<PollModification> getPollDetail(@PathVariable("eventName") String eventName,
                                                   @PathVariable("pollId") Long pollId,
                                                   Principal principal) {
        if(StringUtils.isEmpty(eventName) || pollId == null) {
            return ResponseEntity.badRequest().build();
        }
        accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.getSingleForEvent(pollId, eventName).map(PollModification::from));
    }

    @PostMapping
    ResponseEntity<Long> createNewPoll(@PathVariable("eventName") String eventName,
                                       @RequestBody PollModification form,
                                       Principal principal) {
        if(form == null || !form.isValid()) {
            return ResponseEntity.badRequest().build();
        }
        accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.createNewPoll(eventName, form));
    }

    @PostMapping("/{pollId}")
    ResponseEntity<PollModification> updatePoll(@PathVariable("eventName") String eventName,
                                                @PathVariable("pollId") Long pollId,
                                                @RequestBody PollModification form,
                                                Principal principal) {
        if(form == null || !form.isValid(pollId)) {
            return ResponseEntity.badRequest().build();
        }
        accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.updatePoll(eventName, form).map(PollModification::from));
    }

    @DeleteMapping("/{pollId}")
    ResponseEntity<Boolean> deletePoll(@PathVariable("eventName") String eventName,
                                       @PathVariable("pollId") Long pollId,
                                       Principal principal) {
        var eventAndOrganizationId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.ok(pollManager.deletePoll(eventAndOrganizationId, pollId));
    }

    @DeleteMapping("/{pollId}/option/{optionId}")
    ResponseEntity<PollModification> removeOption(@PathVariable("eventName") String eventName,
                                                 @PathVariable("pollId") Long pollId,
                                                 @PathVariable("optionId") Long optionId,
                                                 Principal principal) {
        var event = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.removeOption(event, pollId, optionId).map(PollModification::from));
    }

    @PutMapping("/{pollId}")
    ResponseEntity<PollModification> updateStatus(@PathVariable("eventName") String eventName,
                                                  @PathVariable("pollId") Long pollId,
                                                  @RequestBody UpdatePollStatusForm form,
                                                  Principal principal) {
        if(form.status == Poll.PollStatus.DRAFT) {
            return ResponseEntity.badRequest().build();
        }
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.updateStatus(pollId, eventAndOrgId, form.status).map(PollModification::from));
    }

    @GetMapping("/{pollId}/filter-tickets")
    ResponseEntity<List<PollParticipant>> findAdditionalAttendees(@PathVariable("eventName") String eventName,
                                                                  @PathVariable("pollId") Long pollId,
                                                                  @RequestParam("filter") String filter,
                                                                  Principal principal) {
        if(StringUtils.isBlank(filter)) {
            return ResponseEntity.badRequest().build();
        }
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.searchTicketsToAllow(eventAndOrgId, pollId, filter));
    }

    @PostMapping("/{pollId}/allow")
    ResponseEntity<Boolean> allowAttendees(@PathVariable("eventName") String eventName,
                                           @PathVariable("pollId") Long pollId,
                                           @RequestBody UpdateParticipantsForm form,
                                           Principal principal) {

        if(CollectionUtils.isEmpty(form.ticketIds)) {
            return ResponseEntity.badRequest().build();
        }
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.ok(pollManager.allowTicketsToVote(eventAndOrgId, form.ticketIds, pollId));
    }

    @GetMapping("/{pollId}/allowed")
    ResponseEntity<List<PollParticipant>> getAllowedAttendees(@PathVariable("eventName") String eventName,
                                                              @PathVariable("pollId") Long pollId,
                                                              Principal principal) {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.ok(pollManager.fetchAllowedTickets(eventAndOrgId, pollId));
    }

    @DeleteMapping("/{pollId}/allowed")
    ResponseEntity<List<PollParticipant>> forbidAttendees(@PathVariable("eventName") String eventName,
                                                          @PathVariable("pollId") Long pollId,
                                                          @RequestBody UpdateParticipantsForm form,
                                                          Principal principal) {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.ok(pollManager.removeParticipants(eventAndOrgId, form.ticketIds, pollId));
    }

    @GetMapping("/{pollId}/stats")
    ResponseEntity<PollStatistics> getStatisticsForEvent(@PathVariable("eventName") String eventName,
                                                         @PathVariable("pollId") Long pollId,
                                                         Principal principal) {
        var eventAndOrgId = accessService.checkEventOwnership(principal, eventName);
        return ResponseEntity.of(pollManager.getStatisticsFor(eventAndOrgId, pollId));
    }

    static class UpdatePollStatusForm {
        private final Poll.PollStatus status;

        @JsonCreator
        UpdatePollStatusForm(@JsonProperty("status") Poll.PollStatus status) {
            this.status = status;
        }
    }

    static class UpdateParticipantsForm {
        private final List<Integer> ticketIds;

        @JsonCreator
        UpdateParticipantsForm(@JsonProperty("ticketIds") List<Integer> ticketIds) {
            this.ticketIds = ticketIds;
        }
    }


}
