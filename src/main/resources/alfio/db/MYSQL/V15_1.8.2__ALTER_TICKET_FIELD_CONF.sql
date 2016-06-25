--
-- This file is part of alf.io.
--
-- alf.io is free software: you can redistribute it and/or modify
-- it under the terms of the GNU General Public License as published by
-- the Free Software Foundation, either version 3 of the License, or
-- (at your option) any later version.
--
-- alf.io is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU General Public License for more details.
--
-- You should have received a copy of the GNU General Public License
-- along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
--

-- drop old constraints
alter table ticket_field_configuration drop index unique_ticket_field_configuration;

-- recreate foreign key constraint, dropped by java migration
alter table ticket_field_configuration add FOREIGN KEY event_id_fk (event_id_fk) REFERENCES event(id);

create table additional_service_field_value(
    additional_service_id_fk int not null,
    ticket_field_configuration_id_fk int not null,
    field_value varchar(2048),
    PRIMARY KEY (additional_service_id_fk, ticket_field_configuration_id_fk)
) ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;

alter table additional_service_field_value add foreign key(additional_service_id_fk) references additional_service(id);
alter table additional_service_field_value add foreign key(ticket_field_configuration_id_fk) references ticket_field_configuration(id);