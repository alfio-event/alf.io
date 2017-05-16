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

alter table tickets_reservation add column event_id_fk integer;
alter table tickets_reservation add foreign key(event_id_fk) references event(id);

update tickets_reservation tr set event_id_fk = (select distinct event_id from ticket where tickets_reservation_id = tr.id);

delete from b_transaction where reservation_id in (select id from tickets_reservation where event_id_fk is null);
delete from tickets_reservation where event_id_fk is null;

alter table tickets_reservation alter column event_id_fk SET NOT NULL;
