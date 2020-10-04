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
package alfio.model.poll;

import ch.digitalfondue.npjt.ConstructorAnnotationRowMapper.Column;
import lombok.Getter;

@Getter
public class PollParticipant {

    private final int id;
    private final String firstName;
    private final String lastName;
    private final String emailAddress;
    private final String categoryName;


    public PollParticipant(@Column("t_id") int id,
                           @Column("t_first_name") String firstName,
                           @Column("t_last_name") String lastName,
                           @Column("t_email_address") String emailAddress,
                           @Column("tc_name") String categoryName) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.categoryName = categoryName;
    }
}
