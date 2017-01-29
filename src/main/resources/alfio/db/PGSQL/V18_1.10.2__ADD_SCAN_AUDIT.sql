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

create table scan_audit (
    ticket_uuid varchar(255) not null,
    event_id_fk integer not null,
    scan_ts timestamp not null,
    username varchar(255) not null,
    check_in_status varchar(255) not null,
    operation varchar(255) not null
);

alter table scan_audit add foreign key(event_id_fk) references event(id);