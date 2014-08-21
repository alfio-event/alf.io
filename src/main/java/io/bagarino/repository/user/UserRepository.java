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
package io.bagarino.repository.user;

import io.bagarino.datamapper.Bind;
import io.bagarino.datamapper.Query;
import io.bagarino.datamapper.QueryRepository;
import io.bagarino.model.user.User;

import java.util.List;

@QueryRepository
public interface UserRepository {
    @Query("SELECT * FROM user")
    List<User> findAll();

    @Query("SELECT * FROM user WHERE id = :userId")
    User findById(@Bind("userId") int userId);

    @Query("select * from user where username = :username")
    User findByUsername(@Bind("username") String username);

    @Query("INSERT INTO user(username, password, first_name, last_name, email_address, enabled) VALUES"
            + " (:username, :password, :first_name, :last_name, :email_address, :enabled)")
    int create(@Bind("username") String username, @Bind("password") String password,
               @Bind("first_name") String firstname, @Bind("last_name") String lastname,
               @Bind("email_address") String emailAddress, @Bind("enabled") boolean enabled);

    @Query("call identity()")
    int getGeneratedUserId();
}
