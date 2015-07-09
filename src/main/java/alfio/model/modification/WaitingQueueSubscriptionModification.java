package alfio.model.modification;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Locale;

@Getter
public class WaitingQueueSubscriptionModification {

    private final String fullName;
    private final String email;
    private final Locale userLanguage;

    @JsonCreator
    public WaitingQueueSubscriptionModification(@JsonProperty("fullName") String fullName,
                                                @JsonProperty("email") String email,
                                                @JsonProperty("userLanguage") Locale userLanguage) {
        this.fullName = fullName;
        this.email = email;
        this.userLanguage = userLanguage;
    }
}
