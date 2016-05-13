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

import alfio.model.user.*;
import alfio.repository.user.AuthorityRepository;
import alfio.repository.user.OrganizationRepository;
import alfio.repository.user.UserRepository;
import alfio.repository.user.join.UserOrganizationRepository;
import alfio.util.PasswordGenerator;
import alfio.util.ValidationResult;
import ch.digitalfondue.npjt.AffectedRowCountAndKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

@Component
public class UserManager {

    public static final String ADMIN_USERNAME = "admin";
    private static final Function<Integer, Integer> ID_EVALUATOR = id -> Optional.ofNullable(id).orElse(Integer.MIN_VALUE);
    private final AuthorityRepository authorityRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserManager(AuthorityRepository authorityRepository,
                       OrganizationRepository organizationRepository,
                       UserOrganizationRepository userOrganizationRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.authorityRepository = authorityRepository;
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Authority> getUserAuthorities(User user) {
        return authorityRepository.findGrantedAuthorities(user.getUsername());
    }

    public List<User> findAllUsers(String username) {
        return findUserOrganizations(username)
                .stream()
                .flatMap(o -> userOrganizationRepository.findByOrganizationId(o.getId()).stream())
                .map(uo -> userRepository.findById(uo.getUserId()))
                .filter(User::isEnabled)
                .collect(toList());
    }

    public User findUserByUsername(String username) {
        return userRepository.findEnabledByUsername(username).orElseThrow(IllegalArgumentException::new);
    }

    public User findUser(int id) {
        return userRepository.findById(id);
    }

    public Collection<Role> getAvailableRoles(String username) {
        User user = findUserByUsername(username);
        return isAdmin(user) || isOwner(user) ? EnumSet.of(Role.OWNER, Role.OPERATOR, Role.SPONSOR) : Collections.emptySet();
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
        return findUserOrganizations(userRepository.getByUsername(username));
    }

    public Organization findOrganizationById(int id, String username) {
        return findUserOrganizations(username)
                .stream()
                .filter(o -> o.getId() == id)
                .findFirst()
                .orElseThrow(IllegalArgumentException::new);
    }

    public List<Organization> findUserOrganizations(User user) {
        if (isAdmin(user)) {
            return organizationRepository.findAll();
        }
        return userOrganizationRepository.findByUserId(user.getId())
                .stream()
                .map(uo -> organizationRepository.getById(uo.getOrganizationId()))
                .collect(toList());
    }

    public boolean isAdmin(User user) {
        return checkRole(user, a -> a.getRole().equals(Role.ADMIN));
    }

    public boolean isOwner(User user) {
        return checkRole(user, a -> a.getRole().equals(Role.ADMIN) || a.getRole().equals(Role.OWNER));
    }

    public boolean isOwnerOfOrganization(User user, int organizationId) {
        return isAdmin(user) || (isOwner(user) && userOrganizationRepository.findByUserId(user.getId()).stream().anyMatch(uo -> uo.getOrganizationId() == organizationId));
    }

    private boolean checkRole(User user, Predicate<Authority> matcher) {
        return getUserAuthorities(user).stream().anyMatch(matcher);
    }

    @Transactional
    public void createOrganization(String name, String description, String email) {
        organizationRepository.create(name, description, email);
    }

    @Transactional
    public void updateOrganization(Integer id, String name, String email, String description) {
        organizationRepository.update(id, name, description, email);
    }

    public ValidationResult validateOrganization(Integer id, String name, String email, String description) {
        int orgId = ID_EVALUATOR.apply(id);
        final long existing = organizationRepository.findByName(name)
                .stream()
                .filter(o -> o.getId() != orgId)
                .count();
        if(existing > 0) {
            return ValidationResult.failed(new ValidationResult.ValidationError("name", "There is already another organization with the same name."));
        }
        Validate.notBlank(name, "name can't be empty");
        Validate.notBlank(email, "email can't be empty");
        Validate.notBlank(description, "description can't be empty");
        return ValidationResult.success();
    }

    @Transactional
    public void editUser(int id, int organizationId, String username, String firstName, String lastName, String emailAddress, Role role, String currentUsername) {
        boolean admin = ADMIN_USERNAME.equals(username) && Role.ADMIN == role;
        if(!admin) {
            int userOrganizationResult = userOrganizationRepository.updateUserOrganization(id, organizationId);
            Assert.isTrue(userOrganizationResult == 1, "unexpected error during organization update");
        }
        int userResult = userRepository.update(id, username, firstName, lastName, emailAddress);
        Assert.isTrue(userResult == 1, "unexpected error during user update");
        if(!admin) {
            Assert.isTrue(getAvailableRoles(currentUsername).contains(role), "cannot assign role "+role);
            authorityRepository.revokeAll(username);
            authorityRepository.create(username, role.getRoleName());
        }
    }

    @Transactional
    public UserWithPassword insertUser(int organizationId, String username, String firstName, String lastName, String emailAddress, Role role) {
        Organization organization = organizationRepository.getById(organizationId);
        String userPassword = PasswordGenerator.generateRandomPassword();
        AffectedRowCountAndKey<Integer> result = userRepository.create(username, passwordEncoder.encode(userPassword), firstName, lastName, emailAddress, true);
        userOrganizationRepository.create(result.getKey(), organization.getId());
        authorityRepository.create(username, role.getRoleName());
        return new UserWithPassword(userRepository.findById(result.getKey()), userPassword, UUID.randomUUID().toString());
    }

    @Transactional
    public UserWithPassword resetPassword(int userId) {
        User user = findUser(userId);
        String password = PasswordGenerator.generateRandomPassword();
        Validate.isTrue(userRepository.resetPassword(userId, passwordEncoder.encode(password)) == 1, "error during password reset");
        return new UserWithPassword(user, password, UUID.randomUUID().toString());
    }

    @Transactional
    public boolean updatePassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username).stream().findFirst().orElseThrow(IllegalStateException::new);
        Validate.isTrue(PasswordGenerator.isValid(newPassword), "invalid password");
        Validate.isTrue(userRepository.resetPassword(user.getId(), passwordEncoder.encode(newPassword)) == 1, "error during password update");
        return true;
    }

    @Transactional
    public void deleteUser(int userId, String currentUsername) {
        User currentUser = userRepository.findEnabledByUsername(currentUsername).orElseThrow(IllegalArgumentException::new);
        Assert.isTrue(userId != currentUser.getId(), "sorry but you cannot commit suicide");
        Assert.isTrue(userRepository.toggleEnabled(userId, false) == 1, "unexpected update result");
    }

    public ValidationResult validateUser(Integer id, String username, int organizationId, String role, String firstName, String lastName, String emailAddress) {
        int userId = ID_EVALUATOR.apply(id);
        final long existing = userRepository.findByUsername(username)
                .stream()
                .filter(u -> u.getId() != userId)
                .count();
        if(existing > 0) {
            return ValidationResult.failed(new ValidationResult.ValidationError("username", "There is already another user with the same username."));
        }
        return ValidationResult.of(Arrays.asList(Pair.of(firstName, "firstName"), Pair.of(lastName, "lastName"), Pair.of(emailAddress, "emailAddress"))
            .stream()
            .filter(p -> StringUtils.isEmpty(p.getKey()))
            .map(p -> new ValidationResult.ValidationError(p.getKey(), p.getValue() + " is required"))
            .collect(toList()));
    }

    public ValidationResult validateNewPassword(String username, String oldPassword, String newPassword, String newPasswordConfirm) {
        return userRepository.findByUsername(username)
            .stream()
            .findFirst()
            .map(u -> {
                List<ValidationResult.ValidationError> errors = new ArrayList<>();
                Optional<String> password = userRepository.findPasswordByUsername(username);
                if(!password.filter(p -> passwordEncoder.matches(oldPassword, p)).isPresent()) {
                    errors.add(new ValidationResult.ValidationError("old-password-invalid", "wrong password"));
                }
                if(!PasswordGenerator.isValid(newPassword)) {
                    errors.add(new ValidationResult.ValidationError("new-password-invalid", "new password is not strong enough"));
                }
                if(!StringUtils.equals(newPassword, newPasswordConfirm)) {
                    errors.add(new ValidationResult.ValidationError("new-password-does-not-match", "new password has not been confirmed"));
                }
                return ValidationResult.of(errors);
            })
            .orElseGet(ValidationResult::failed);
    }


}
