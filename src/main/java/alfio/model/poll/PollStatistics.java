package alfio.model.poll;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class PollStatistics {
    private final int totalVotes;
    private final int allowedParticipants;
    private final List<PollOptionStatistics> optionStatistics;
}
