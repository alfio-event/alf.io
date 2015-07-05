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

create table event_migration (
  id serial PRIMARY KEY not null,
  event_id integer not null,
  current_version varchar(1024) not null,
  build_ts TIMESTAMP not null,
  status varchar(255)
);
alter table event_migration add foreign key(event_id) references event(id);
alter table event_migration add constraint "unique_event_id" unique(event_id);