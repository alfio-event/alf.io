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
package alfio.repository;

import alfio.datamapper.Bind;
import alfio.datamapper.Query;
import alfio.datamapper.QueryRepository;
import alfio.model.Customer;

import java.util.List;

@QueryRepository
public interface CustomerRepository {

	@Query("SELECT * FROM customer")
	List<Customer> findAll();

	@Query("SELECT * FROM customer WHERE id = :userId")
    Customer findById(@Bind("userId") int userId);

	@Query("INSERT INTO customer(username, password, first_name, last_name, address, zip, city, state, country, email_address) VALUES"
			+ " (:username, :password, :first_name, :last_name, :address, :zip, :city, :state, :country, :email_address)")
	int create(@Bind("username") String username, @Bind("password") String password,
			@Bind("first_name") String firstname, @Bind("last_name") String lastname, @Bind("address") String address,
			@Bind("zip") String zip, @Bind("city") String city, @Bind("state") String state,
			@Bind("country") String country, @Bind("email_address") String emailAddress);
}
