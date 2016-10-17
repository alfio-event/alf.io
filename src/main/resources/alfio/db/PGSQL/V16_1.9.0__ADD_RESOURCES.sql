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

create table resource_global (
    name varchar(255) primary key not null,
    content_size integer not null,
    content bytea not null,
    content_type varchar(255) not null,
    creation_time timestamp default now() not null,
    attributes text
);

create table resource_organizer (
    name varchar(255) not null,
    organization_id_fk integer not null,
    content_size integer not null,
    content bytea not null,
    content_type varchar(255) not null,
    creation_time timestamp default now() not null,
    attributes text
);
alter table resource_organizer add constraint unique_resource_organizer unique(name, organization_id_fk);
alter table resource_organizer add foreign key(organization_id_fk) references organization(id);

create table resource_event (
    name varchar(255) primary key not null,
    organization_id_fk integer not null,
    event_id_fk integer not null,
    content_size integer not null,
    content bytea not null,
    content_type varchar(255) not null,
    creation_time timestamp default now() not null,
    attributes text
);
alter table resource_event add constraint unique_resource_event unique(name, organization_id_fk, event_id_fk);
alter table resource_event add foreign key(organization_id_fk) references organization(id);
alter table resource_event add foreign key(event_id_fk) references event(id);