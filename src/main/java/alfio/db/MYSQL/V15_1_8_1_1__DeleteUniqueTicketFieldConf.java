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
package alfio.db.MYSQL;

import org.flywaydb.core.api.migration.spring.SpringJdbcMigration;
import org.springframework.jdbc.core.JdbcTemplate;

public class V15_1_8_1_1__DeleteUniqueTicketFieldConf implements SpringJdbcMigration {
    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        jdbcTemplate.queryForList("SELECT constraint_name FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE constraint_schema = (select database() from dual) AND table_name = 'ticket_field_configuration'", String.class)
            .forEach(constraint -> jdbcTemplate.execute(String.format("alter table ticket_field_configuration drop foreign key %s", constraint)));
    }


}
