package io.bagarino.controller.api;

import io.bagarino.manager.user.UserManager;
import io.bagarino.model.user.Organization;
import io.bagarino.model.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class AdminApiController {

    private final UserManager userManager;

    @Autowired
    public AdminApiController(UserManager userManager) {
        this.userManager = userManager;
    }


    @RequestMapping("/organizations")
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations(Principal principal) {
        return userManager.findUserOrganizations(principal.getName());
    }

    @RequestMapping("/users")
    public List<User> getAllUsers(Principal principal) {
        return userManager.findAllUsers();
    }

}
