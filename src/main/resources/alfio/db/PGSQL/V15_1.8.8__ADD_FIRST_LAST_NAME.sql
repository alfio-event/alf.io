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

-- event
alter table event add column version varchar(50);

-- ticket
alter table ticket add column first_name varchar(255);
alter table ticket add column last_name varchar(255);

-- reservation
alter table tickets_reservation add column first_name varchar(255);
alter table tickets_reservation add column last_name varchar(255);

-- waiting queue
alter table waiting_queue add column first_name varchar(255);
alter table waiting_queue add column last_name varchar(255);