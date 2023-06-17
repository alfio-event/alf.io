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
import alfio.model.modification.PollModification;
import alfio.model.modification.PollOptionModification;
import alfio.model.poll.*;
import alfio.model.result.ErrorCode;
import alfio.model.result.Result;
import alfio.repository.*;
import alfio.util.Json;
import alfio.util.PinGenerator;
import lombok.AllArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNullElse;

@Component
@AllArgsConstructor
@Transactional
public class PollManager {
    private final PollRepository pollRepository;
    private final EventRepository eventRepository;
    private final TicketRepository ticketRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TicketSearchRepository ticketSearchRepository;
    private final AuditingRepository auditingRepository;

    public Result<List<Poll>> getActiveForEvent(String eventName, String pin) {
        return validatePinAndEvent(pin, eventName)
            .flatMap(eventAndTicket -> Result.success(pollRepository.findActiveForEvent(eventAndTicket.getLeft().getId())));
    }

    public Result<PollWithOptions> getSingleActiveForEvent(String eventName, Long id, String pin) {
        return validatePinAndEvent(pin, eventName)
            .flatMap(eventAndTicket -> {
                var optionalPoll = pollRepository.findSingleActiveForEvent(eventAndTicket.getLeft().getId(), id);
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
                Validate.isTrue(pollRepository.checkPollOption(optionId, pollId, event.getId()) == 1, "Invalid selection");
                Validate.isTrue(pollRepository.registerAnswer(pollId, optionId, ticket.getId(), event.getOrganizationId()) == 1, "Unexpected error while inserting answer");
                return Result.success(true);
            });
    }

    // admin
    public List<Poll> getAllForEvent(String eventName) {
        var eventOptional = eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName);
        if(eventOptional.isEmpty()) {
            return List.of();
        }
        return pollRepository.findAllForEvent(eventOptional.get().getId());
    }

    public Optional<PollWithOptions> getSingleForEvent(Long pollId, String eventName) {
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .flatMap(event -> getSingleForEvent(pollId, event));
    }

    private Optional<PollWithOptions> getSingleForEvent(Long pollId, EventAndOrganizationId event) {
        return pollRepository.findSingleForEvent(event.getId(), Objects.requireNonNull(pollId))
            .map(poll -> new PollWithOptions(poll, pollRepository.getOptionsForPoll(pollId)));
    }

    public Optional<Long> createNewPoll(String eventName, PollModification form) {
        Validate.isTrue(form.isValid());
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .map(event -> {
                List<String> tags = form.isAccessRestricted() ? List.of(UUID.randomUUID().toString()) : List.of();
                var pollKey = pollRepository.insert(
                    form.getTitle(),
                    requireNonNullElse(form.getDescription(), Map.of()),
                    tags,
                    form.getOrder(),
                    event.getId(),
                    event.getOrganizationId()
                );
                Validate.isTrue(pollKey.getAffectedRowCount() == 1);
                insertOptions(form.getOptions(), event, pollKey.getKey());
                return pollKey.getKey();
            });
    }

    public boolean deletePoll(EventAndOrganizationId event, Long pollId) {
        Validate.isTrue(pollId != null);
        Validate.isTrue(pollRepository.deletePoll(pollId, event.getId(), event.getOrganizationId()) == 1);
        return true;
    }

    public Optional<PollWithOptions> updatePoll(String eventName, PollModification form) {
        Validate.isTrue(form.isValid(form.getId()));
        return eventRepository.findOptionalEventAndOrganizationIdByShortName(eventName)
            .flatMap(event -> {
                var pollId = form.getId();
                var existingPollWithOptions = getSingleForEvent(pollId, event).orElseThrow();
                var existingPoll = existingPollWithOptions.getPoll();
                var tags = existingPoll.getAllowedTags();
                if(form.isAccessRestricted() == existingPoll.getAllowedTags().isEmpty()) {
                    tags = form.isAccessRestricted() ? List.of(UUID.randomUUID().toString()) : List.of();
                }
                Validate.isTrue(pollRepository.update(form.getTitle(), form.getDescription(), tags, form.getOrder(), pollId, event.getId()) == 1);
                // options
                // find if there is any new option
                var newOptions = form.getOptions().stream().filter(pm -> pm.getId() == null).collect(Collectors.toList());
                if(!newOptions.isEmpty()) {
                    insertOptions(newOptions, event, pollId);
                }
                // update existing options
                var existingOptions = form.getOptions().stream().filter(pm -> pm.getId() != null).collect(Collectors.toList());
                if(!existingOptions.isEmpty()) {
                    updateOptions(existingOptions, event, pollId);
                }

                return getSingleForEvent(pollId, event);
            });
    }

    public Optional<PollWithOptions> updateStatus(Long pollId, EventAndOrganizationId event, Poll.PollStatus newStatus) {
        Validate.isTrue(newStatus != Poll.PollStatus.DRAFT, "can't revert to draft");
        Validate.isTrue(pollRepository.updateStatus(newStatus, pollId, event.getId()) == 1, "Error while updating status");
        return getSingleForEvent(pollId, event);
    }

    public Optional<List<PollParticipant>> searchTicketsToAllow(EventAndOrganizationId event, Long pollId, String filter) {
        Validate.isTrue(StringUtils.isNotBlank(filter));
        return pollRepository.findSingleForEvent(event.getId(), pollId)
            .map(p -> ticketSearchRepository.filterConfirmedTicketsInEventForPoll(event.getId(), 20, "%"+filter+"%", p.getAllowedTags()));
    }

    public boolean allowTicketsToVote(EventAndOrganizationId event, List<Integer> ids, long pollId) {
        Validate.isTrue(CollectionUtils.isNotEmpty(ids));
        var poll = pollRepository.findSingleForEvent(event.getId(), pollId).orElseThrow();
        Validate.isTrue(CollectionUtils.isNotEmpty(poll.getAllowedTags()));
        var tag = poll.getAllowedTags().get(0);
        var result = ticketRepository.tagTickets(ids, event.getId(), tag);
        Validate.isTrue(ids.size() == result, "Unable to tag tickets");
        var auditingResults = auditingRepository.registerTicketTag(ids, List.of(Map.of("tag", tag)));
        Validate.isTrue(auditingResults == ids.size(), "Error while writing auditing");
        return true;
    }

    public List<PollParticipant> removeParticipants(EventAndOrganizationId event, List<Integer> ticketIds, long pollId) {
        var poll = pollRepository.findSingleForEvent(event.getId(), pollId).orElseThrow();
        Validate.isTrue(CollectionUtils.isNotEmpty(poll.getAllowedTags()));
        var tag = poll.getAllowedTags().get(0);
        var result = ticketRepository.untagTickets(ticketIds, event.getId(), tag);
        Validate.isTrue(result == 1, "Error while removing tag");
        var auditingResults = auditingRepository.registerTicketUntag(ticketIds, List.of(Map.of("tag", tag)));
        Validate.isTrue(auditingResults == ticketIds.size(), "Error while writing auditing");
        return ticketRepository.getTicketsForEventByTags(event.getId(), poll.getAllowedTags());
    }

    public Optional<PollWithOptions> removeOption(EventAndOrganizationId event, Long pollId, Long optionId) {
        var poll = pollRepository.findSingleForEvent(event.getId(), pollId).orElseThrow();
        Validate.isTrue(pollRepository.deleteOption(pollId, optionId) == 1, "Error while deleting option");
        return getSingleForEvent(poll.getId(), event);
    }

    public List<PollParticipant> fetchAllowedTickets(EventAndOrganizationId event, long pollId) {
        var poll = pollRepository.findSingleForEvent(event.getId(), pollId).orElseThrow();
        return ticketRepository.getTicketsForEventByTags(event.getId(), poll.getAllowedTags());
    }

    public Optional<PollStatistics> getStatisticsFor(EventAndOrganizationId event, long pollId) {
        return pollRepository.findSingleForEvent(event.getId(), pollId)
            .map(p -> {
                int allowedParticipants;
                if(p.getAllowedTags().isEmpty()) {
                    allowedParticipants = eventRepository.findStatisticsFor(event.getId()).getCheckedInTickets();
                } else {
                    allowedParticipants = ticketRepository.countTicketsMatchingTagsAndStatus(event.getId(), p.getAllowedTags(), List.of(Ticket.TicketStatus.CHECKED_IN.name()));
                }
                var statistics = pollRepository.getStatisticsFor(p.getId(), event.getId());
                return new PollStatistics(statistics.stream().mapToInt(PollOptionStatistics::getVotes).sum(), allowedParticipants, statistics);
            });
    }

    private void insertOptions(List<PollOptionModification> options, EventAndOrganizationId event, Long pollId) {
        if(options.size() == 1) {
            var option = options.get(0);
            pollRepository.insertOption(pollId,
                option.getTitle(),
                requireNonNullElse(option.getDescription(), Map.of()),
                event.getOrganizationId());
        } else {
            var parameterSources = options.stream()
                .map(option -> new MapSqlParameterSource("pollId", pollId)
                    .addValue("title", Json.toJson(requireNonNullElse(option.getTitle(), Map.of())))
                    .addValue("description", Json.toJson(requireNonNullElse(option.getDescription(), Map.of())))
                    .addValue("organizationId", event.getOrganizationId()))
                .toArray(MapSqlParameterSource[]::new);
            int[] results = jdbcTemplate.batchUpdate(pollRepository.bulkInsertOptions(), parameterSources);
            Validate.isTrue(IntStream.of(results).sum() == options.size(), "Unexpected result from update.");
        }
    }

    private void updateOptions(List<PollOptionModification> existingOptions, EventAndOrganizationId event, Long pollId) {
        var parameterSources = existingOptions.stream()
            .map(option -> new MapSqlParameterSource("pollId", pollId)
                .addValue("title", Json.toJson(requireNonNullElse(option.getTitle(), Map.of())))
                .addValue("description", Json.toJson(requireNonNullElse(option.getDescription(), Map.of())))
                .addValue("id", option.getId()))
            .toArray(MapSqlParameterSource[]::new);
        int[] results = jdbcTemplate.batchUpdate(pollRepository.bulkUpdateOptions(), parameterSources);
        Validate.isTrue(IntStream.of(results).sum() == existingOptions.size(), "Unexpected result from update.");
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
