package alfio.model.poll;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class PollOptionStatistics {

    private final int votes;
    private final long optionId;

    public PollOptionStatistics(@Column("votes") int votes,
                                @Column("poll_option_id_fk") long optionId) {
        this.votes = votes;
        this.optionId = optionId;
    }
}
