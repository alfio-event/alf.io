package alfio.model.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Delegate;

@Data
public class UserWithPassword {
    @Delegate
    private final User user;
    private final String password;
    private final String uniqueId;

    @JsonIgnore
    public User getUser() {
        return user;
    }
}