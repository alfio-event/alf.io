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

create table configuration_organization(
  id serial primary key not null,
  organization_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_organization add constraint "unique_configuration_organization" unique(organization_id_fk, c_key);
alter table configuration_organization add foreign key(organization_id_fk) references organization(id);

create table configuration_event(
  id serial primary key not null,
  organization_id_fk integer not null,
  event_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_event add constraint "unique_configuration_event" unique(organization_id_fk, event_id_fk, c_key);
alter table configuration_event add foreign key(organization_id_fk) references organization(id);
alter table configuration_event add foreign key(event_id_fk) references event(id);

create table configuration_ticket_category(
  id serial primary key not null,
  organization_id_fk integer not null,
  event_id_fk integer not null,
  ticket_category_id_fk integer not null,
  c_key varchar(255) not null,
  c_value varchar(2048) not null,
  description varchar(2048)
);
alter table configuration_ticket_category add constraint "unique_configuration_ticket_category" unique(organization_id_fk, event_id_fk, ticket_category_id_fk, c_key);
alter table configuration_ticket_category add foreign key(organization_id_fk) references organization(id);
alter table configuration_ticket_category add foreign key(event_id_fk) references event(id);
alter table configuration_ticket_category add foreign key(ticket_category_id_fk) references ticket_category(id);