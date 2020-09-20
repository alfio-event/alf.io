package alfio.model.modification;

import alfio.model.poll.Poll;
import alfio.model.poll.PollWithOptions;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
public class PollModification {

    private final Long id;
    private final Map<String, String> title;
    private final Map<String, String> description;
    private final int order;
    private final List<PollOptionModification> options;
    private final boolean accessRestricted;
    private final Poll.PollStatus status;


    @JsonCreator
    public PollModification(@JsonProperty("id") Long id,
                            @JsonProperty("title") Map<String, String> title,
                            @JsonProperty("description") Map<String, String> description,
                            @JsonProperty("order") Integer order,
                            @JsonProperty("options") List<PollOptionModification> options,
                            @JsonProperty("accessRestricted") boolean accessRestricted,
                            @JsonProperty("status") Poll.PollStatus status) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.order = Objects.requireNonNullElse(order, 0);
        this.options = options;
        this.accessRestricted = Objects.requireNonNullElse(accessRestricted, false);
        this.status = status;
    }

    public boolean isValid(boolean update) {
        return (!update || id != null)
            && MapUtils.isNotEmpty(title)
            && CollectionUtils.isNotEmpty(options)
            && options.stream().allMatch(p -> p.isValid(update));
    }

    public static PollModification from(Poll poll) {
        return new PollModification(poll.getId(),
            poll.getTitle(),
            poll.getDescription(),
            poll.getOrder(),
            List.of(),
            !poll.getAllowedTags().isEmpty(),
            poll.getStatus());
    }

    public static PollModification from(PollWithOptions pollWithOptions) {
        var poll = pollWithOptions.getPoll();
        return new PollModification(poll.getId(),
            poll.getTitle(),
            poll.getDescription(),
            poll.getOrder(),
            pollWithOptions.getOptions().stream().map(PollOptionModification::from).collect(Collectors.toList()),
            !poll.getAllowedTags().isEmpty(),
            poll.getStatus());
    }
}
