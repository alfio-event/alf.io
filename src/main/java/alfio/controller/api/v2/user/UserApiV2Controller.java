package alfio.controller.api.v2.user;

import alfio.controller.api.v2.model.User;
import alfio.manager.user.UserManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/public/user")
@RequiredArgsConstructor
public class UserApiV2Controller {

    private final UserManager userManager;

    @GetMapping("/me")
    public ResponseEntity<User> getUserIdentity(Authentication authentication) {
        if(authentication != null) {
            return userManager.findOptionalEnabledUserByUsername(authentication.getName())
                .map(u -> ResponseEntity.ok(new User(u.getFirstName(), u.getLastName(), u.getEmailAddress())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
