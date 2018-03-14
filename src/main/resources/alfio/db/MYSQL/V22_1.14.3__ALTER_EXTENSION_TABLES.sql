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

alter table extension_support add column es_id integer auto_increment not null primary key;

drop table extension_event;
create table extension_event (
    es_id_fk integer not null unique,
    event varchar(128) not null
)  ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;
alter table extension_event add foreign key(es_id_fk) references extension_support(es_id);


drop table extension_configuration_metadata_value;
drop table extension_configuration_metadata;
drop table extension_configuration_param_registry;

create table extension_configuration_metadata (
    ecm_id integer auto_increment primary key not null,
    ecm_es_id_fk integer not null,
    ecm_name varchar(64) not null,
    ecm_configuration_level varchar(32) not null, -- [event, organization, event], for each -> new entry
    ecm_description varchar(1024),
    ecm_type varchar(20) not null,
    ecm_mandatory boolean not null
)  ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;
alter table extension_configuration_metadata add foreign key(ecm_es_id_fk) references extension_support(es_id) on delete cascade;
alter table extension_configuration_metadata add constraint unique_extension_configuration_metadata unique(ecm_es_id_fk, ecm_name, ecm_configuration_level);

create table extension_configuration_metadata_value (
    fk_ecm_id integer not null,
    conf_path varchar(128) not null,
    conf_value varchar(1024) not null
)  ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;
alter table extension_configuration_metadata_value add constraint unique_extension_configuration_metadata_value unique(fk_ecm_id, conf_path);
alter table extension_configuration_metadata_value add foreign key(fk_ecm_id) references extension_configuration_metadata(ecm_id)  on delete cascade;


drop table extension_log;
create table extension_log (
    id integer auto_increment primary key not null,
    path varchar(128) not null,
    effective_path varchar(128) not null,
    name varchar(64) not null,
    description mediumtext not null,
    type varchar(255),
    event_ts timestamp DEFAULT CURRENT_TIMESTAMP
)  ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;