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
package alfio.manager.user;

import alfio.manager.system.ConfigurationLevel;
import alfio.manager.system.ConfigurationManager;
import alfio.model.result.ValidationResult;
import alfio.model.system.Configuration;
import alfio.model.user.*;
import alfio.model.user.join.UserOrganization;
import alfio.repository.InvoiceSequencesRepository;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.PasswordGenerator;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static alfio.model.system.ConfigurationKeys.ENABLE_TICKET_TRANSFER;
import static alfio.model.system.ConfigurationKeys.WRITE_USER_CREDENTIAL_FOR_JITSI_SYNC;
import static java.util.stream.Collectors.toList;

@Component
@Transactional
@RequiredArgsConstructor
public class UserManager {

    public static final String ADMIN_USERNAME = "admin";
    private final AuthorityRepository authorityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvoiceSequencesRepository invoiceSequencesRepository;

    private List<Authority> getUserAuthorities(User user) {
        return authorityRepository.findGrantedAuthorities(user.getUsername());
    }

    public List<UserWithOrganizations> findAllUsers(String username) {
        List<Organization> organizations = findUserOrganizations(username);
        Predicate<Collection<?>> isNotEmpty = ks -> !ks.isEmpty();
        return Optional.of(organizations)
            .filter(isNotEmpty)
            .flatMap(org -> {
                Map<Integer, List<UserOrganization>> usersAndOrganizations = userOrganizationRepository.findByOrganizationIdsOrderByUserId(organizations.stream().map(Organization::getId).collect(toList()))
                    .stream()
                    .collect(Collectors.groupingBy(UserOrganization::getUserId));
                return Optional.of(usersAndOrganizations.keySet())
                    .filter(isNotEmpty)
                    .map(ks -> userRepository.findByIds(ks)
                        .stream()
                        .map(u -> {
                            List<UserOrganization> userOrganizations = usersAndOrganizations.get(u.getId());
                            List<Organization> filteredOrganizations = organizations.stream().filter(o -> userOrganizations.stream().anyMatch(uo -> uo.getOrganizationId() == o.getId())).collect(toList());
                            List<Role> roles = authorityRepository.findRoles(u.getUsername()).stream().map(Role::fromRoleName).collect(toList());
                            return new UserWithOrganizations(u, filteredOrganizations, roles);
                        }).collect(toList()));
            }).orElseGet(Collections::emptyList);
    }

    public List<User> findAllEnabledUsers(String username) {
        return findUserOrganizations(username)
                .stream()
                .flatMap(o -> userOrganizationRepository.findByOrganizationId(o.getId()).stream())
                .map(uo -> userRepository.findById(uo.getUserId()))
                .filter(User::isEnabled)
                .collect(toList());
    }

    public List<User> findAllApiKeysFor(int organizationId) {
        return userRepository.findAllApiKeysForOrganization(organizationId);
    }

    public User findUserByUsername(String username) {
        return userRepository.findEnabledByUsername(username).orElseThrow(IllegalArgumentException::new);
    }

    public Optional<User> findOptionalEnabledUserByUsername(String username) {
        return userRepository.findEnabledByUsername(username);
    }

    public boolean usernameExists(String username) {
        return userRepository.findIdByUserName(username).isPresent();
    }

    public User findUser(int id) {
        return userRepository.findById(id);
    }

    public Collection<Role> getAvailableRoles(String username) {
        User user = findUserByUsername(username);
        return isAdmin(user) || isOwner(user) ? EnumSet.of(Role.OWNER, Role.OPERATOR, Role.SUPERVISOR, Role.SPONSOR, Role.API_CONSUMER) : Collections.emptySet();
    }

    /**
     * Return the most privileged role of a user
     * @param user
     * @return user role
     */
    public Role getUserRole(User user) {
        return getUserAuthorities(user).stream().map(Authority::getRole).sorted().findFirst().orElse(Role.OPERATOR);
    }

    public List<Organization> findUserOrganizations(String username) {
        return organizationRepository.findAllForUser(username);
    }

    public Organization findOrganizationById(int id, String username) {
        return findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == id)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    public boolean isAdmin(User user) {
        return checkRole(user, Collections.singleton(Role.ADMIN));
    }

    public boolean isOwner(User user) {
        return checkRole(user, EnumSet.of(Role.ADMIN, Role.OWNER, Role.API_CONSUMER));
    }

    public boolean isOwnerOfOrganization(User user, int organizationId) {
        return isAdmin(user) || (isOwner(user) && userOrganizationRepository.findByUserId(user.getId()).stream().anyMatch(uo -> uo.getOrganizationId() == organizationId));
    }

    public boolean isOwnerOfOrganization(String username, int organizationId) {
        return userRepository.findByUsername(username)
            .filter(user -> isOwnerOfOrganization(user, organizationId))
            .isPresent();
    }

    private boolean checkRole(User user, Set<Role> expectedRoles) {
        Set<String> roleNames = expectedRoles.stream().map(Role::getRoleName).collect(Collectors.toSet());
        return authorityRepository.checkRole(user.getUsername(), roleNames);
    }

    public int createOrganization(String name, String description, String email) {
        organizationRepository.create(name, description, email);
        int orgId = organizationRepository.getIdByName(name);
        invoiceSequencesRepository.initFor(orgId);
        return orgId;
    }

    public void updateOrganization(Integer id, String name, String email, String description) {
        organizationRepository.update(id, name, description, email);
    }

    public ValidationResult validateOrganization(Integer id, String name, String email, String description) {
        if(id == null && organizationRepository.findByName(name).isPresent()) {
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("name", "There is already another organization with the same name."));
        }
        Validate.notBlank(name, "name can't be empty");
        Validate.notBlank(email, "email can't be empty");
        Validate.notBlank(description, "description can't be empty");
        return ValidationResult.success();
    }

    public void editUser(int id, int organizationId, String username, String firstName, String lastName, String emailAddress, String description, Role role, String currentUsername) {
        boolean admin = ADMIN_USERNAME.equals(username) && Role.ADMIN == role;
        if(!admin) {
            int userOrganizationResult = userOrganizationRepository.updateUserOrganization(id, organizationId);
            Assert.isTrue(userOrganizationResult == 1, "unexpected error during organization update");
        }
        int userResult = userRepository.update(id, username, firstName, lastName, emailAddress, description);
        Assert.isTrue(userResult == 1, "unexpected error during user update");
        if(!admin && !username.equals(currentUsername)) {
            Assert.isTrue(getAvailableRoles(currentUsername).contains(role), "cannot assign role "+role);
            authorityRepository.revokeAll(username);
            authorityRepository.create(username, role.getRoleName());
        }
    }

    public void updateUserContactInfo(int id, String firstName, String lastName, String emailAddress) {
        userRepository.updateContactInfo(id, firstName, lastName, emailAddress);
    }


    public UserWithPassword insertUser(int organizationId, String username, String firstName, String lastName, String emailAddress, Role role, User.Type userType) {
        return insertUser(organizationId, username, firstName, lastName, emailAddress, role, userType, null, null);
    }


    public UserWithPassword insertUser(int organizationId, String username, String firstName, String lastName, String emailAddress, Role role, User.Type userType, ZonedDateTime validTo, String description) {
        if (userType == User.Type.API_KEY) {
            username = UUID.randomUUID().toString();
            firstName = "apikey";
            lastName = "";
            emailAddress = "";
        }

        String userPassword = PasswordGenerator.generateRandomPassword();
        return insertUser(organizationId, username, firstName, lastName, emailAddress, role, userType, userPassword, validTo, description);
    }

    public void bulkInsertApiKeys(int organizationId, Role role, List<String> descriptions) {
        for (String description : descriptions) {
            insertUser(organizationId, null, null, null, null, role, User.Type.API_KEY, null, description);
        }
    }


    public UserWithPassword insertUser(int organizationId, String username, String firstName, String lastName, String emailAddress, Role role, User.Type userType, String userPassword, ZonedDateTime validTo, String description) {
        Organization organization = organizationRepository.getById(organizationId);
        AffectedRowCountAndKey<Integer> result = userRepository.create(username, passwordEncoder.encode(userPassword), firstName, lastName, emailAddress, true, userType, validTo, description);
        userOrganizationRepository.create(result.getKey(), organization.getId());
        authorityRepository.create(username, role.getRoleName());
        return new UserWithPassword(userRepository.findById(result.getKey()), userType != User.Type.API_KEY ? userPassword : "", UUID.randomUUID().toString());
    }


    public UserWithPassword resetPassword(int userId) {
        User user = findUser(userId);
        String password = PasswordGenerator.generateRandomPassword();
        Validate.isTrue(userRepository.resetPassword(userId, passwordEncoder.encode(password)) == 1, "error during password reset");
        return new UserWithPassword(user, password, UUID.randomUUID().toString());
    }


    public boolean updatePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username).orElseThrow(IllegalStateException::new);
        Validate.isTrue(PasswordGenerator.isValid(newPassword), "invalid password");
        Validate.isTrue(userRepository.resetPassword(user.getId(), passwordEncoder.encode(newPassword)) == 1, "error during password update");
        return true;
    }


    public void deleteUser(int userId, String currentUsername) {
        User currentUser = userRepository.findEnabledByUsername(currentUsername).orElseThrow(IllegalArgumentException::new);
        Assert.isTrue(userId != currentUser.getId(), "sorry but you cannot delete your own account.");
        userRepository.deleteUserAndReferences(userId);
    }

    public void enable(int userId, String currentUsername, boolean status) {
        User currentUser = userRepository.findEnabledByUsername(currentUsername).orElseThrow(IllegalArgumentException::new);
        Assert.isTrue(userId != currentUser.getId(), "sorry but you cannot commit suicide");

        userRepository.toggleEnabled(userId, status);
    }

    public ValidationResult validateUser(Integer id, String username, String firstName, String lastName, String emailAddress) {

        Optional<User> existing = Optional.ofNullable(id).flatMap(userRepository::findOptionalById);

        if(existing.filter(e -> e.getUsername().equals(username)).isEmpty() && usernameExists(username)) {
            return ValidationResult.failed(new ValidationResult.ErrorDescriptor("username", "There is already another user with the same username."));
        }
        return ValidationResult.of(Stream.of(Pair.of(firstName, "firstName"), Pair.of(lastName, "lastName"), Pair.of(emailAddress, "emailAddress"))
            .filter(p -> StringUtils.isEmpty(p.getKey()))
            .map(p -> new ValidationResult.ErrorDescriptor(p.getKey(), p.getValue() + " is required"))
            .collect(toList()));
    }

    public ValidationResult validateNewPassword(String username, String oldPassword, String newPassword, String newPasswordConfirm) {
        return userRepository.findByUsername(username)
            .map(u -> {
                List<ValidationResult.ErrorDescriptor> errors = new ArrayList<>();
                Optional<String> password = userRepository.findPasswordByUsername(username);
                if(password.filter(p -> oldPassword == null || passwordEncoder.matches(oldPassword, p)).isEmpty()) {
                    errors.add(new ValidationResult.ErrorDescriptor("alfio.old-password-invalid", "wrong password"));
                }
                if(!PasswordGenerator.isValid(newPassword)) {
                    errors.add(new ValidationResult.ErrorDescriptor("alfio.new-password-invalid", "new password is not strong enough"));
                }
                if(!StringUtils.equals(newPassword, newPasswordConfirm)) {
                    errors.add(new ValidationResult.ErrorDescriptor("alfio.new-password-does-not-match", "new password has not been confirmed"));
                }
                return ValidationResult.of(errors);
            })
            .orElseGet(ValidationResult::failed);
    }

    public static boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication) {
            return ((Authentication) principal).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        }
        return false;
    }
}
