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
package alfio.manager;

import alfio.model.EventAndOrganizationId;
import alfio.model.Ticket;
import alfio.model.poll.Poll;
import alfio.model.poll.PollWithOptions;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.EventRepository;
import alfio.repository.PollRepository;
import alfio.repository.TicketRepository;
import alfio.util.PinGenerator;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

@Component
@AllArgsConstructor
public class PollManager {
    private final PollRepository pollRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;

    public Result<List<Poll>> getActiveForEvent(String eventName, String pin) {
        return validatePinAndEvent(pin, eventName)
            .flatMap(event -> Result.success(pollRepository.findActiveInEvent(event.getRight().getId())));
    }

    public Result<PollWithOptions> getSingleActiveForEvent(String eventName, Long id, String pin) {
        return validatePinAndEvent(pin, eventName)
            .flatMap(eventAndTicket -> {
                var optionalPoll = pollRepository.findSingleActiveInEvent(eventAndTicket.getLeft().getId(), id);
                if(optionalPoll.isEmpty()) {
                    return Result.error(ErrorCode.custom("not_found", ""));
                }
                return Result.success(new PollWithOptions(optionalPoll.get(), pollRepository.getOptionsForPoll(id)));
             });

    }

    public Result<Boolean> registerAnswer(String eventName, Long pollId, Long optionId, String pin) {

        if(pollId == null || optionId == null) {
            return Result.error(ErrorCode.custom("not_found", ""));
        }

        return validatePinAndEvent(pin, eventName)
            .flatMap(eventAndTicket -> {
                var event = eventAndTicket.getLeft();
                var ticket = eventAndTicket.getRight();
                Assert.isTrue(pollRepository.checkPollOption(optionId, pollId, event.getId()) == 1, "Invalid selection");
                Assert.isTrue(pollRepository.registerAnswer(pollId, optionId, ticket.getId(), event.getOrganizationId()) == 1, "Unexpected error while inserting answer");
                return Result.success(true);
            });
    }

    private Result<Pair<EventAndOrganizationId, Ticket>> validatePinAndEvent(String pin, String eventName) {
        var eventOptional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        return new Result.Builder<EventAndOrganizationId>()
            .checkPrecondition(eventOptional::isPresent, ErrorCode.EventError.NOT_FOUND)
            .checkPrecondition(() -> PinGenerator.isPinValid(pin), ErrorCode.custom("pin.invalid", ""))
            .build(eventOptional::get)
            .flatMap(event -> {
                var partialUuid = PinGenerator.pinToPartialUuid(pin);
                // find checkedIn ticket
                var tickets = ticketRepository.findByEventIdAndPartialUUIDForUpdate(event.getId(), partialUuid + "%", Ticket.TicketStatus.CHECKED_IN);
                int numResults = tickets.size();
                if(numResults != 1) {
                    return Result.error(ErrorCode.custom(numResults > 1 ? "pin.duplicate" : "pin.invalid", ""));
                }
                return Result.success(Pair.of(event, tickets.get(0)));
            });
    }

}
