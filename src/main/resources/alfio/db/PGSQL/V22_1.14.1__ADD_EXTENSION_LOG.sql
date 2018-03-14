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

create table extension_log (
    id serial primary key not null,
    path_fk varchar(128) not null,
    name_fk varchar(64 ) not null,
    description text not null,
    type varchar(255),
    event_ts timestamp DEFAULT CURRENT_TIMESTAMP
);

alter table extension_log add foreign key(path_fk, name_fk) references extension_support(path, name);