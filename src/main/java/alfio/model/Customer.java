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
package alfio.model;

import alfio.datamapper.ConstructorAnnotationRowMapper;
import lombok.Getter;

@Getter
public class Customer {
    private final int id;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String address;
    private final String zip;
    private final String city;
    private final String state;
    private final String country;
    private final String emailAddress;

    public Customer(@ConstructorAnnotationRowMapper.Column("id") int id, @ConstructorAnnotationRowMapper.Column("username") String username, @ConstructorAnnotationRowMapper.Column("first_name") String firstName,
                    @ConstructorAnnotationRowMapper.Column("last_name") String lastName, @ConstructorAnnotationRowMapper.Column("address") String address, @ConstructorAnnotationRowMapper.Column("zip") String zip,
                    @ConstructorAnnotationRowMapper.Column("city") String city, @ConstructorAnnotationRowMapper.Column("state") String state, @ConstructorAnnotationRowMapper.Column("country") String country,
                    @ConstructorAnnotationRowMapper.Column("email_address") String emailAddress) {
        this.id = id;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.zip = zip;
        this.city = city;
        this.state = state;
        this.country = country;
        this.emailAddress = emailAddress;
    }
}
