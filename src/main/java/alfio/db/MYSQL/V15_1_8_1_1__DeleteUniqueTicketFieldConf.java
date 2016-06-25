package alfio.db.MYSQL;

import org.flywaydb.core.api.migration.spring.SpringJdbcMigration;
import org.springframework.jdbc.core.JdbcTemplate;

public class V15_1_8_1_1__DeleteUniqueTicketFieldConf implements SpringJdbcMigration {
    @Override
    public void migrate(JdbcTemplate jdbcTemplate) throws Exception {
        jdbcTemplate.queryForList("SELECT constraint_name FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE constraint_schema = (select database() from dual) AND table_name = 'ticket_field_configuration'", String.class)
            .stream()
            .forEach(constraint -> jdbcTemplate.execute(String.format("alter table ticket_field_configuration drop foreign key %s", constraint)));
    }


}
