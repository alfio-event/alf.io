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

create table plugin_log(
  id integer auto_increment primary key not null,
  plugin_id varchar(255) not null,
  event_id int not null,
  description MEDIUMTEXT not null,
  type varchar(255),
  event_ts timestamp  not null
) ENGINE=InnoDB CHARACTER SET=utf8 COLLATE utf8_bin;
alter table plugin_log add foreign key(event_id) references event(id);
