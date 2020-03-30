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

alter table event add column format varchar(255) not null default 'IN_PERSON';
alter table event alter column location drop not null;
alter table event add constraint "check_location_if_in_person" check (format = 'ONLINE' OR location is not null);
alter table event add column metadata jsonb not null default '{}';
alter table ticket_category add column metadata jsonb not null default '{}';
