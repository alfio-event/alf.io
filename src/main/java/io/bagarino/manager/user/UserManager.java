/**
 * This file is part of bagarino.
 *
 * bagarino is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bagarino is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with bagarino.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.bagarino.manager.user;

import io.bagarino.model.user.Authority;
import io.bagarino.model.user.Organization;
import io.bagarino.model.user.User;
import io.bagarino.repository.user.AuthorityRepository;
import io.bagarino.repository.user.OrganizationRepository;
import io.bagarino.repository.user.UserRepository;
import io.bagarino.repository.user.join.UserOrganizationRepository;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UserManager {

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

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public List<Organization> findUserOrganizations(String username) {
        return findUserOrganizations(userRepository.findByUsername(username));
    }

    public List<Organization> findUserOrganizations(User user) {
        if(isAdmin(user)) {
            return organizationRepository.findAll();
        }
        return userOrganizationRepository.findByUserId(user.getId())
                .stream()
                .map(uo -> organizationRepository.findById(uo.getOrganizationId()))
                .collect(Collectors.toList());
    }

    public boolean isAdmin(User user) {
        return getUserAuthorities(user).stream().anyMatch(a -> a.getRole().equals(AuthorityRepository.ROLE_ADMIN));
    }

    public List<User> findMembers(Organization organization) {
        return userOrganizationRepository.findByOrganizationId(organization.getId())
                .stream()
                .map(uo -> userRepository.findById(uo.getUserId()))
                .collect(Collectors.toList());
    }

    public void createOrganization(String name, String description) {
        organizationRepository.create(name, description);
    }

    @Transactional
    public void createUser(int organizationId, String username, String firstName, String lastName, String emailAddress) {
        Organization organization = organizationRepository.findById(organizationId);
        String userPassword = Long.toHexString(UUID.randomUUID().getMostSignificantBits());
        Pair<Integer, Integer> result = userRepository.create(username, passwordEncoder.encode(userPassword), firstName, lastName, emailAddress, true);
        userOrganizationRepository.create(result.getLeft(), organization.getId());
        authorityRepository.create(username, AuthorityRepository.ROLE_OWNER);
    }

}
