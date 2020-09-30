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

import alfio.controller.form.PollVoteForm;
import alfio.manager.PollManager;
import alfio.manager.support.response.ValidatedResponse;
import alfio.model.poll.Poll;
import alfio.model.poll.PollWithOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/public/event/{eventName}/poll")
@RequiredArgsConstructor
public class PollApiController {

    private final PollManager pollManager;

    @GetMapping("")
    ResponseEntity<ValidatedResponse<List<Poll>>> getAll(@PathVariable("eventName") String eventName, @RequestParam("pin") String pin) {
        var result = pollManager.getActiveForEvent(eventName, pin);
        if(result.isSuccess()) {
            return ResponseEntity.ok(ValidatedResponse.fromResult(result, "pin"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ValidatedResponse.fromResult(result, "pin"));
    }

    @GetMapping("/{pollId}")
    ResponseEntity<ValidatedResponse<PollWithOptions>> getSingle(@PathVariable("eventName") String eventName, @PathVariable("pollId") Long pollId, @RequestParam("pin") String pin) {
        var result = pollManager.getSingleActiveForEvent(eventName, pollId, pin);
        if(result.isSuccess()) {
            return ResponseEntity.ok(ValidatedResponse.fromResult(result, "pin"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ValidatedResponse.fromResult(result, "pin"));
    }

    @PostMapping("/{pollId}/answer")
    ResponseEntity<ValidatedResponse<Boolean>> registerAnswer(@PathVariable("eventName") String eventName,
                                     @PathVariable("pollId") Long pollId,
                                     @RequestBody PollVoteForm form) {

        var result = pollManager.registerAnswer(eventName, pollId, form.getOptionId(), form.getPin());
        if(result.isSuccess()) {
            return ResponseEntity.ok(ValidatedResponse.fromResult(result, "pin"));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ValidatedResponse.fromResult(result, "pin"));
    }

}
