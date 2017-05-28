package alfio.model.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.util.List;

@RequiredArgsConstructor
@Getter
public class UserWithOrganizations {
    @Delegate
    private final User user;
    private final List<Organization> memberOf;
}
