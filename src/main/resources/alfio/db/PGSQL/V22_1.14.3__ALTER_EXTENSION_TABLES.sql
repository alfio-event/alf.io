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

delete from extension_configuration_param_registry;
delete from extension_log;
delete from extension_event;
delete from extension_support;

alter table extension_support add column es_id serial not null primary key;

drop table extension_event;
create table extension_event (
    es_id_fk integer not null unique,
    event varchar(128) not null
);
alter table extension_event add foreign key(es_id_fk) references extension_support(es_id);

alter table extension_configuration_metadata drop constraint "unique_extension_configuration_metadata";
alter table extension_configuration_metadata drop column ecm_fk_id;

alter table extension_configuration_metadata add column ecm_es_id_fk integer not null;
alter table extension_configuration_metadata add foreign key(ecm_es_id_fk) references extension_support(es_id) on delete cascade;
alter table extension_configuration_metadata add constraint "unique_extension_configuration_metadata" unique(ecm_es_id_fk, ecm_name, ecm_configuration_level);
drop table extension_configuration_param_registry;

drop table extension_log;
create table extension_log (
    id serial primary key not null,
    path varchar(128) not null,
    effective_path varchar(128) not null,
    name varchar(64) not null,
    description text not null,
    type varchar(255),
    event_ts timestamp DEFAULT CURRENT_TIMESTAMP
);