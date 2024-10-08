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

drop view if exists basic_event_with_optional_subscription;
create view basic_event_with_optional_subscription as (
    select e.*,
           s.id as subscription_id,
           o.slug as org_slug
        from event e
        left join subscription_event se on se.event_id_fk = e.id
        left join subscription_descriptor sd on se.subscription_descriptor_id_fk = sd.id
        left join subscription s on sd.id = s.subscription_descriptor_fk
        left join organization o on e.org_id = o.id
);
