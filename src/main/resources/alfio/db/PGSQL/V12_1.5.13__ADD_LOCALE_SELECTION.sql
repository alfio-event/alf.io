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

-- 0001 -> it, 0010 -> en, 0100 -> de : 0111 -> all: 7
ALTER TABLE event ADD COLUMN locales integer DEFAULT 7 not null;


CREATE TABLE event_description_text (
    event_id_fk integer not null,
    locale varchar(8) not null,
    type varchar(16) not null,
    description varchar(2048) not null
);

alter table event_description_text add PRIMARY KEY (event_id_fk, locale, type);
alter table event_description_text add foreign key(event_id_fk) references event(id);

CREATE TABLE ticket_category_text(
    ticket_category_id_fk integer not null,
    locale varchar(8) not null,
    description varchar(1024) not null
);

alter table ticket_category_text add PRIMARY KEY (ticket_category_id_fk, locale);
alter table ticket_category_text add foreign key(ticket_category_id_fk) references ticket_category(id);