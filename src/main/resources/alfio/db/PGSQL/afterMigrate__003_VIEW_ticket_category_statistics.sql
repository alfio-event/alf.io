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

create view ticket_category_statistics as (select
  res.*,
  is_expired and not_sold_tickets > 0 as is_containing_orphan_tickets,
  stuck_count > 0 as is_containing_stuck_tickets
from

(select
  id as ticket_category_id,
  access_restricted,
  max_tickets,
  bounded,
  is_expired,
  event_id,
  coalesce(pending_count,0) as pending_count,
  coalesce(checked_in_count,0) as checked_in_count,
  coalesce(sold_tickets_count,0) as sold_tickets_count,
  coalesce(released_count, 0) as released_count,
  case(bounded) when false then 0 else max_tickets - coalesce(sold_tickets_count,0 )  - coalesce(checked_in_count, 0) -  coalesce(pending_count, 0) end as not_sold_tickets,
  coalesce(stuck_count, 0) as stuck_count,
  category_configuration
from

(select max_tickets, bounded, id, event_id, expiration < now() as is_expired, access_restricted from ticket_category where tc_status = 'ACTIVE' ) ticket_cat

left join

(select
  sum(case(status = 'PENDING') when true then 1 else 0 end) as pending_count,
  sum(case(status = 'RELEASED') when true then 1 else 0 end) as released_count,
  sum(case(status = 'CHECKED_IN') when true then 1 else 0 end) checked_in_count,
  sum(case(status in ('TO_BE_PAID', 'ACQUIRED')) when true then 1 else 0 end) as sold_tickets_count,
  category_id
from ticket
inner join ticket_category tc
on category_id = tc.id
group by category_id
) tickets_stats on ticket_cat.id = tickets_stats.category_id

left join

(select count(*) stuck_count, category_id
  from ticket
  inner join tickets_reservation on tickets_reservation.id = tickets_reservation_id
  where tickets_reservation.status = 'STUCK'
  group by category_id) stuck_count on ticket_cat.id = stuck_count.category_id

left join
(select ticket_category_id_fk, jsonb_agg(json_build_object('id', id, 'key', c_key, 'value', c_value, 'configurationPathLevel', 'TICKET_CATEGORY')) category_configuration
  from configuration_ticket_category group by 1) tc_settings on ticket_cat.id = tc_settings.ticket_category_id_fk

) as res);