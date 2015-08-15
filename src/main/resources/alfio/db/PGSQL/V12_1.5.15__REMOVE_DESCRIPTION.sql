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

insert into event_description_text (SELECT id as event_id_fk, 'en' as locale, 'DESCRIPTION' as type, description FROM event where locales = 7 and ((select count(*) from event_description_text where event_id_fk = id) = 0)
union all
SELECT id as event_id_fk, 'de' as locale, 'DESCRIPTION' as type, description FROM event where locales = 7 and ((select count(*) from event_description_text where event_id_fk = id) = 0)
union all
SELECT id as event_id_fk, 'it' as locale, 'DESCRIPTION' as type, description FROM event where locales = 7 and ((select count(*) from event_description_text where event_id_fk = id) = 0)
);


insert into ticket_category_text ((SELECT ticket_category.id as ticket_category_id_fk, 'en' as locale, description FROM TICKET_CATEGORY where description is not null and  ((select locales from event where event.id = event_id) = 7) and (select count(*) from ticket_category_text where ticket_category_id_fk = ticket_category.id) = 0)
union all
(SELECT ticket_category.id as ticket_category_id_fk, 'de' as locale, description FROM TICKET_CATEGORY where description is not null and ((select locales from event where event.id = event_id) = 7) and (select count(*) from ticket_category_text where ticket_category_id_fk = ticket_category.id) = 0)
union all
(SELECT ticket_category.id as ticket_category_id_fk, 'it' as locale, description FROM TICKET_CATEGORY where description is not null and ((select locales from event where event.id = event_id) = 7) and (select count(*) from ticket_category_text where ticket_category_id_fk = ticket_category.id) = 0));

ALTER TABLE event DROP COLUMN description;
ALTER TABLE ticket_category DROP COLUMN  description;