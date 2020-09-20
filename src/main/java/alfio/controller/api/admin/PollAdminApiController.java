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

import alfio.manager.PollManager;
import alfio.model.modification.PollModification;
import alfio.model.poll.Poll;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api/{eventName}/poll")
@RequiredArgsConstructor
public class PollAdminApiController {

    private final PollManager pollManager;

    @GetMapping
    ResponseEntity<List<PollModification>> getAllForEvent(@PathVariable("eventName") String eventName) {
        if(StringUtils.isEmpty(eventName)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(pollManager.getAllForEvent(eventName).stream().map(PollModification::from).collect(Collectors.toList()));
    }

    @GetMapping("/{pollId}")
    ResponseEntity<PollModification> getPollDetail(@PathVariable("eventName") String eventName,
                                                   @PathVariable("pollId") Long pollId) {
        if(StringUtils.isEmpty(eventName) || pollId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(pollManager.getSingleForEvent(pollId, eventName).map(PollModification::from));
    }

    @PostMapping
    ResponseEntity<Long> createNewPoll(@PathVariable("eventName") String eventName,
                                       @RequestBody PollModification form) {
        if(form == null || !form.isValid(false)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(pollManager.createNewPoll(eventName, form));
    }

    @PostMapping("/{pollId}")
    ResponseEntity<PollModification> updatePoll(@PathVariable("eventName") String eventName,
                                                @PathVariable("pollId") Long pollId,
                                                @RequestBody PollModification form) {
        if(form == null || !form.isValid(true) || !pollId.equals(form.getId())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(pollManager.updatePoll(eventName, form).map(PollModification::from));
    }

    @PutMapping("/{pollId}")
    ResponseEntity<PollModification> updateStatus(@PathVariable("eventName") String eventName,
                                                  @PathVariable("pollId") Long pollId,
                                                  @RequestBody UpdatePollStatusForm form) {
        if(form.status == Poll.PollStatus.DRAFT) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.of(pollManager.updateStatus(pollId, eventName, form.status).map(PollModification::from));
    }

    static class UpdatePollStatusForm {
        private final Poll.PollStatus status;

        @JsonCreator
        UpdatePollStatusForm(@JsonProperty("status") Poll.PollStatus status) {
            this.status = status;
        }
    }


}
