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

create table auditing (
    reservation_id varchar(512),
    user_id int,
    event_type varchar(128),
    event_time timestamp not null,
    entity_type varchar(64),
    entity_id varchar(512),
    modifications text
);

create index "auditing_reservation_id_idx" on auditing(reservation_id);

create view auditing_user as (select * from auditing left join ba_user on auditing.user_id = ba_user.id)