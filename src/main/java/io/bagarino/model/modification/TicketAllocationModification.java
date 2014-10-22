package io.bagarino.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class TicketAllocationModification {
    private final int srcCategoryId;
    private final int targetCategoryId;
    private final int eventId;

    @JsonCreator
    public TicketAllocationModification(@JsonProperty("srcCategoryId") int srcCategoryId,
                                        @JsonProperty("targetCategoryId") int targetCategoryId,
                                        @JsonProperty("eventId") int eventId) {
        this.srcCategoryId = srcCategoryId;
        this.targetCategoryId = targetCategoryId;
        this.eventId = eventId;
    }
}
