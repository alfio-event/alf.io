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


alter table auditing add column event_id int;
create index "auditing_event_id_idx" ON auditing(event_id);

update auditing set event_id= (select event_id_fk from tickets_reservation where reservation_id = id);

create view latest_ticket_update as select cast(entity_id as int) ticket_id, event_id, max(event_time) last_update from auditing where entity_type = 'TICKET' group by ticket_id, event_id;
