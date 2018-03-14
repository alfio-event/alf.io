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

create table extension_configuration_param_registry (
    ecpr_id serial primary key not null,
    ecpr_ec_identifier varchar(64) not null,
    ecpr_path varchar(128) not null
);
alter table extension_configuration_param_registry add constraint "unique_extension_configuration_param_registry" unique(ecpr_ec_identifier, ecpr_path);

create table extension_configuration_metadata (
    ecm_id serial primary key not null,
    ecm_fk_id integer not null,
    ecm_name varchar(64) not null,
    ecm_configuration_level varchar(32) not null, -- [event, organization, event], for each -> new entry
    ecm_description varchar(1024),
    ecm_type varchar(20) not null,
    ecm_mandatory boolean not null
);
alter table extension_configuration_metadata add constraint "unique_extension_configuration_metadata" unique(ecm_fk_id, ecm_name, ecm_configuration_level);
alter table extension_configuration_metadata add foreign key(ecm_fk_id) references extension_configuration_param_registry(ecpr_id)  on delete cascade;

create table extension_configuration_metadata_value (
    fk_ecm_id integer not null,
    conf_path varchar(128) not null,
    conf_value varchar(1024) not null
);
alter table extension_configuration_metadata_value add constraint "unique_extension_configuration_metadata_value" unique(fk_ecm_id, conf_path);
alter table extension_configuration_metadata_value add foreign key(fk_ecm_id) references extension_configuration_metadata(ecm_id)  on delete cascade;