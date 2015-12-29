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

alter table ticket add column locked_assignment boolean default false;

alter table ticket add column job_title varchar(255) ;
alter table ticket add column company varchar(255);
alter table ticket add column phone_number varchar(255);
alter table ticket add column address varchar(450);
alter table ticket add column country varchar(255);
alter table ticket add column tshirt_size varchar(32);


-- event
alter table event add column image_url varchar(2048);