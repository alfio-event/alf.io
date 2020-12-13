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

drop view if exists checkin_ticket_event_and_category_info;
create type EVENT_FORMAT as enum ('IN_PERSON', 'ONLINE', 'HYBRID');
alter table event
    alter column format drop default,
    alter column format type EVENT_FORMAT USING format::event_format,
    alter column format set default 'IN_PERSON'::EVENT_FORMAT;

create type TICKET_ACCESS_TYPE as enum ('INHERIT', 'IN_PERSON', 'ONLINE');
alter table ticket_category add column ticket_access_type TICKET_ACCESS_TYPE not null default 'INHERIT';
