/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.controller.api.admin;

import alfio.config.WebSecurityConfig;
import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.manager.user.UserManager;
import alfio.model.modification.OrganizationModification;
import alfio.model.modification.UserModification;
import alfio.model.result.ValidationResult;
import alfio.model.system.ConfigurationKeys;
import alfio.model.user.*;
import alfio.repository.user.OrganizationRepository;
import alfio.util.ImageUtil;
import alfio.util.Json;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static alfio.model.system.ConfigurationKeys.WRITE_USER_CREDENTIAL_FOR_JITSI_SYNC;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.trimToNull;

@RestController
@RequestMapping("/admin/api")
@Log4j2
@AllArgsConstructor
public class UsersApiController {

    private static final String OK = "OK";
    private final UserManager userManager;
    private final OrganizationRepository organizationRepository;
    private final ConfigurationManager configurationManager;

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public String unhandledException(Exception e) {
        log.error("unhandled exception", e);
        return e != null ? e.getMessage() : "Unexpected error";
    }

    @GetMapping("/roles")
    public Collection<RoleDescriptor> getAllRoles(Principal principal) {
        return userManager.getAvailableRoles(principal.getName()).stream().map(RoleDescriptor::new).collect(Collectors.toList());
    }

    /**
     * This endpoint is intended only for external use. If a user is registered as "sponsor", then the answer will be "SPONSOR", otherwise "OPERATOR".
     * @return "SPONSOR" or "OPERATOR", depending on current user's privileges.
     */
    @GetMapping("/user-type")
    public String getLoggedUserType() {
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .map(s -> StringUtils.substringAfter(s, "ROLE_"))
            .filter(WebSecurityConfig.SPONSOR::equals)
            .findFirst()
            .orElse(WebSecurityConfig.OPERATOR);
    }

    @GetMapping("/user/details")
    public Map<String, String> retrieveDetails(Principal principal) {
        User user = userManager.findUserByUsername(principal.getName());
        Map<String, String> result = new HashMap<>();
        boolean isApiKey = user.getType() == User.Type.API_KEY;
        result.put(isApiKey ? "apiKey" : "username", user.getUsername());
        if(!isApiKey) {
            result.put("firstName", user.getFirstName());
            result.put("lastName", user.getLastName());
        }
        result.put("description", user.getDescription());
        result.put("userType", getLoggedUserType());
        return result;
    }

    @GetMapping("/organizations")
    @ResponseStatus(HttpStatus.OK)
    public List<Organization> getAllOrganizations(Principal principal) {
        return userManager.findUserOrganizations(principal.getName());
    }

    @GetMapping("/organizations/{id}")
    public Organization getOrganization(@PathVariable("id") int id, Principal principal) {
        return userManager.findOrganizationById(id, principal.getName());
    }

    @GetMapping("/users")
    public List<UserWithOrganizations> getAllUsers(Principal principal) {
        return userManager.findAllUsers(principal.getName());
    }

    @PostMapping("/api-keys/bulk")
    public ResponseEntity<String> bulkCreate(@RequestBody BulkApiKeyCreation request, Principal principal) {
        Optional<User> userOptional = userManager.findOptionalEnabledUserByUsername(principal.getName())
            .filter(u -> userManager.isOwnerOfOrganization(u, request.organizationId));
        if(userOptional.isPresent()) {
            userManager.bulkInsertApiKeys(request.organizationId, request.role, request.descriptions);
            return ResponseEntity.ok("OK");
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/organizations/new")
    public String insertOrganization(@RequestBody OrganizationModification om) {
        userManager.createOrganization(om.getName(), om.getDescription(), om.getEmail());
        return OK;
    }

    @PostMapping("/organizations/update")
    public String updateOrganization(@RequestBody OrganizationModification om) {
        userManager.updateOrganization(om.getId(), om.getName(), om.getEmail(), om.getDescription());
        return OK;
    }

    @PostMapping("/organizations/check")
    public ValidationResult validateOrganization(@RequestBody OrganizationModification om) {
        return userManager.validateOrganization(om.getId(), om.getName(), om.getEmail(), om.getDescription());
    }

    @PostMapping("/users/check")
    public ValidationResult validateUser(@RequestBody UserModification userModification) {
        if(userModification.getType() == User.Type.API_KEY) {
            return ValidationResult.success();
        } else {
            return userManager.validateUser(userModification.getId(), userModification.getUsername(),
                    userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress());
        }
    }

    @PostMapping("/users/edit")
    public String editUser(@RequestBody UserModification userModification, Principal principal) {
        userManager.editUser(userModification.getId(), userModification.getOrganizationId(),
            userModification.getUsername(), userModification.getFirstName(), userModification.getLastName(),
            userModification.getEmailAddress(), userModification.getDescription(),
            Role.valueOf(userModification.getRole()), principal.getName());
        return OK;
    }

    @PostMapping("/users/new")
    public UserWithPasswordAndQRCode insertUser(@RequestBody UserModification userModification, @RequestParam("baseUrl") String baseUrl, Principal principal) {
        Role requested = Role.valueOf(userModification.getRole());
        Validate.isTrue(userManager.getAvailableRoles(principal.getName()).stream().anyMatch(requested::equals), String.format("Requested role %s is not available for current user", userModification.getRole()));
        User.Type type = userModification.getType();
        UserWithPassword userWithPassword = userManager.insertUser(userModification.getOrganizationId(), userModification.getUsername(),
            userModification.getFirstName(), userModification.getLastName(),
            userModification.getEmailAddress(), requested,
            type == null ? User.Type.INTERNAL : type,
            userModification.getValidToAsDateTime(), userModification.getDescription());
        String qrCode = type != User.Type.API_KEY ? Base64.getEncoder().encodeToString(generateQRCode(userWithPassword, baseUrl)) : null;
        return new UserWithPasswordAndQRCode(userWithPassword, qrCode);
    }

    @GetMapping("/api-keys/organization/{organizationId}/all")
    public void getAllApiKeys(@PathVariable("organizationId") int organizationId, HttpServletResponse response, Principal principal) throws IOException {
        String username = principal.getName();
        if(userManager.isOwnerOfOrganization(username, organizationId)) {
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition", "attachment; filename=apiKeys.zip");

            String baseUrl = configurationManager.getForSystem(ConfigurationKeys.BASE_URL).getRequiredValue();
            try(OutputStream os = response.getOutputStream(); ZipOutputStream zipOS = new ZipOutputStream(os)) {
                for (User user : userManager.findAllApiKeysFor(organizationId)) {
                    Pair<String, byte[]> result = generateApiKeyQRCode(user, baseUrl);
                    zipOS.putNextEntry(new ZipEntry(user.getType().name() + "-" +result.getLeft()+".png"));
                    StreamUtils.copy(result.getRight(), zipOS);
                }
            }
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private static Pair<String, byte[]> generateApiKeyQRCode(User user, String baseUrl) {
        Map<String, String> info = new HashMap<>();
        info.put("apiKey", user.getUsername());
        info.put("baseUrl", baseUrl);
        String description = defaultString(trimToNull(user.getDescription()), user.getUsername());
        return Pair.of(description, ImageUtil.createQRCodeWithDescription(Json.GSON.toJson(info), description));
    }

    private static byte[] generateQRCode(UserWithPassword userWithPassword, String baseUrl) {
        Map<String, Object> info = new HashMap<>();
        info.put("username", userWithPassword.getUsername());
        info.put("password", userWithPassword.getPassword());
        info.put("baseUrl", baseUrl);
        return ImageUtil.createQRCode(Json.GSON.toJson(info));
    }

    @DeleteMapping("/users/{id}")
    public String deleteUser(@PathVariable("id") int userId, Principal principal) {
        userManager.deleteUser(userId, principal.getName());
        return OK;
    }

    @PostMapping("/users/{id}/enable/{enable}")
    public String enableUser(@PathVariable("id") int userId, @PathVariable("enable")boolean enable, Principal principal) {
        userManager.enable(userId, principal.getName(), enable);
        return OK;
    }

    @GetMapping("/users/{id}")
    public UserModification loadUser(@PathVariable("id") int userId) {
        User user = userManager.findUser(userId);
        List<Organization> userOrganizations = userManager.findUserOrganizations(user.getUsername());
        return new UserModification(user.getId(), userOrganizations.get(0).getId(), userManager.getUserRole(user).name(),
            user.getUsername(), user.getFirstName(), user.getLastName(), user.getEmailAddress(),
            user.getType(), user.getValidToEpochSecond(), user.getDescription());
    }

    @GetMapping("/users/current")
    public UserModification loadCurrentUser(Principal principal) {
        User user = userManager.findUserByUsername(principal.getName());
        Optional<Organization> userOrganization = userManager.findUserOrganizations(user.getUsername()).stream().findFirst();
        return new UserModification(user.getId(), userOrganization.map(Organization::getId).orElse(-1),
            userManager.getUserRole(user).name(), user.getUsername(), user.getFirstName(), user.getLastName(),
            user.getEmailAddress(), user.getType(), user.getValidToEpochSecond(), user.getDescription());
    }

    @PostMapping("/users/current/update-password")
    public ValidationResult updateCurrentUserPassword(@RequestBody PasswordModification passwordModification, Principal principal) {
        var res = userManager.validateNewPassword(principal.getName(), passwordModification.oldPassword, passwordModification.newPassword, passwordModification.newPasswordConfirm)
            .ifSuccess(() -> userManager.updatePassword(principal.getName(), passwordModification.newPassword));
        // this check should be inside the userManager but it generates circular dependecy with configuration manager..
        var org = organizationRepository.findAllOrganizationIdForUser(principal.getName());
        if(org.size() > 0 && configurationManager.getFor(WRITE_USER_CREDENTIAL_FOR_JITSI_SYNC, ConfigurationLevel.organization(org.get(0))).getValueAsBooleanOrDefault()) {
            try {
                File directory = new File("/home/alfio/users");
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                Files.write(Paths.get(directory + "/" + principal.getName()), Collections.singleton(passwordModification.newPassword), StandardCharsets.UTF_8);
                log.info("Psw update for User " + principal.getName());
            } catch (IOException e) {
                log.error(e);
            }
        }
        return res;
    }

    @PostMapping("/users/current/edit")
    public void updateCurrentUser(@RequestBody UserModification userModification, Principal principal) {
        User user = userManager.findUserByUsername(principal.getName());
        userManager.updateUserContactInfo(user.getId(), userModification.getFirstName(), userModification.getLastName(), userModification.getEmailAddress());

    }

    @PutMapping("/users/{id}/reset-password")
    public UserWithPasswordAndQRCode resetPassword(@PathVariable("id") int userId, @RequestParam("baseUrl") String baseUrl) {
        UserWithPassword userWithPassword = userManager.resetPassword(userId);
        return new UserWithPasswordAndQRCode(userWithPassword, Base64.getEncoder().encodeToString(generateQRCode(userWithPassword, baseUrl)));
    }

    @Getter
    public static class UserWithPasswordAndQRCode extends UserWithPassword {

        private final String qrCode;

        UserWithPasswordAndQRCode(UserWithPassword userWithPassword, String qrCode) {
            super(userWithPassword.getUser(), userWithPassword.getPassword(), userWithPassword.getUniqueId());
            this.qrCode = qrCode;
        }
    }

    private static final class RoleDescriptor {
        private final Role role;

        RoleDescriptor(Role role) {
            this.role = role;
        }

        public String getRole() {
            return role.name();
        }

        public String getDescription() {
            return role.getDescription();
        }

        public String getTarget() { return role.getTarget().name(); }
    }

    private static final class PasswordModification {

        private final String oldPassword;
        private final String newPassword;
        private final String newPasswordConfirm;

        @JsonCreator
        private PasswordModification(@JsonProperty("oldPassword") String oldPassword,
                                     @JsonProperty("newPassword") String newPassword,
                                     @JsonProperty("newPasswordConfirm") String newPasswordConfirm) {
            this.oldPassword = oldPassword;
            this.newPassword = newPassword;
            this.newPasswordConfirm = newPasswordConfirm;
        }
    }

    private static final class BulkApiKeyCreation {

        private final int organizationId;
        private final Role role;
        private final List<String> descriptions;

        @JsonCreator
        private BulkApiKeyCreation(@JsonProperty("organizationId") int organizationId,
                                   @JsonProperty("role") Role role,
                                   @JsonProperty("descriptions") List<String> descriptions) {
            this.organizationId = organizationId;
            this.role = role;
            this.descriptions = descriptions;
        }
    }
}
