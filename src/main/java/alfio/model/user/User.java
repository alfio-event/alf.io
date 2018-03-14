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
package alfio.model.user;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class User implements Serializable {

    public enum Type {
        INTERNAL, DEMO
    }

    private final int id;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String emailAddress;
    private final boolean enabled;


    public User(@Column("id") int id,
                @Column("username") String username,
                @Column("first_name") String firstName,
                @Column("last_name") String lastName,
                @Column("email_address") String emailAddress,
                @Column("enabled") boolean enabled) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.enabled=enabled;
    }
}
