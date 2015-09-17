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

create table ticket_field_configuration(
    id serial primary key not null,
    event_id_fk integer not null,
    field_name varchar(64) not null,
    field_order int not null,
    field_type varchar(64) not null,
    field_restricted_values varchar(2048),
    field_maxlength int,
    field_minlength int,
    field_required boolean not null
);

alter table ticket_field_configuration add constraint "unique_ticket_field_configuration" unique(event_id_fk, field_name);
alter table ticket_field_configuration add constraint "unique_order_ticket_field_configuration" unique(event_id_fk, field_order);
alter table ticket_field_configuration add foreign key(event_id_fk) references event(id);

create table ticket_field_description(
    ticket_field_configuration_id_fk int not null,
    field_locale varchar(8) not null,
    description varchar(128) not null,
    PRIMARY KEY (ticket_field_configuration_id_fk, field_locale)
);

alter table ticket_field_description add foreign key(ticket_field_configuration_id_fk) references ticket_field_configuration(id);

create table ticket_field_value(
    ticket_id_fk int not null,
    ticket_field_configuration_id_fk int not null,
    field_value varchar(2048),
    PRIMARY KEY (ticket_id_fk, ticket_field_configuration_id_fk)
);

alter table ticket_field_value add foreign key(ticket_id_fk) references ticket(id);
alter table ticket_field_value add foreign key(ticket_field_configuration_id_fk) references ticket_field_configuration(id);