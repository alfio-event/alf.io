package alfio.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SendCodeModification {

    private final String code;
    private final String assignee;
    private final String email;
    private final String language;

    @JsonCreator
    public SendCodeModification(@JsonProperty("code") String code,
                                @JsonProperty("assignee") String assignee,
                                @JsonProperty("email") String email,
                                @JsonProperty("language") String language) {
        this.code = code;
        this.assignee = assignee;
        this.email = email;
        this.language = language;
    }

}
