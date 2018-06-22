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

drop view if exists auditing_user;
CREATE VIEW auditing_user AS
 SELECT auditing.reservation_id,
    auditing.user_id,
    auditing.event_type,
    auditing.event_time,
    auditing.entity_type,
    auditing.entity_id,
    auditing.modifications,
    ba_user.id,
    ba_user.username,
    ba_user.password,
    ba_user.first_name,
    ba_user.last_name,
    ba_user.email_address,
    ba_user.enabled
   FROM auditing
     LEFT JOIN ba_user ON auditing.user_id = ba_user.id;