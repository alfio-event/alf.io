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

drop view if exists events_statistics;
drop view if exists ticket_category_statistics;

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

create view events_statistics as (
select
      event.id,
      (select count(id) from ticket where event_id = event.id and status not in ('INVALIDATED', 'EXPIRED')) as available_seats,
      coalesce( case(contains_unbounded_categories) when true then 0 else (select count(id) from ticket where event_id = event.id and status not in ('INVALIDATED', 'EXPIRED')) - allocated_count end, 0) as not_allocated_tickets,
      coalesce (pending_count, 0) as pending_tickets,
      coalesce (sold_tickets_count, 0) as sold_tickets,
      coalesce ( (select released_count + count(id) from ticket where event_id = event.id and status = 'RELEASED' and category_id is null),0) as released_tickets,
      coalesce(stats.checked_in_count, 0) as checked_in_tickets,
      coalesce (case(contains_unbounded_categories) when true then
        (select count(id) from ticket where event_id = event.id and status not in ('INVALIDATED', 'EXPIRED'))
          - allocated_count
          - released_count
          - sold_tickets_count_unbounded
          - checked_in_count_unbounded
          - pending_count_unbounded
          - (select count(*) from ticket where status = 'RELEASED' and category_id is null and event_id = event.id)
          else 0 end, 0) as dynamic_allocation,
      coalesce (
      	case (contains_unbounded_categories) when true then
        	allocated_count - sold_tickets_count_bounded - checked_in_count_bounded - pending_count_bounded
      	else
        	allocated_count - sold_tickets_count - stats.checked_in_count - pending_count
      	end, 0) as not_sold_tickets,
      coalesce (is_containing_orphan_tickets_count > 0, false) as is_containing_orphan_tickets,
      coalesce (is_containing_stuck_tickets_count > 0, false) as is_containing_stuck_tickets_count,
      coalesce (public_and_valid_count > 0, false) as show_public_statistics
from (
select
	sum(sold_tickets_count) as sold_tickets_count,
	sum(checked_in_count) as checked_in_count,
	sum(pending_count) as pending_count,
	sum(released_count) as released_count,
	sum(case (bounded) when true then checked_in_count else 0 end) as checked_in_count_bounded,
	sum(case (bounded = false) when true then checked_in_count else 0 end) as checked_in_count_unbounded,
	sum(case (bounded) when true then max_tickets else 0 end) as allocated_count,
	sum(case (bounded) when true then sold_tickets_count else 0 end) as sold_tickets_count_bounded,
	sum(case (bounded = false) when true then sold_tickets_count else 0 end) as sold_tickets_count_unbounded,
	sum(case (bounded = false) when true then pending_count else 0 end) as pending_count_unbounded,
	sum(case (bounded) when true then pending_count else 0 end) as pending_count_bounded,
	sum(case (bounded) when false then 1 else 0 end) > 0 contains_unbounded_categories,
	sum(case (is_containing_orphan_tickets) when true then 1 else 0 end) is_containing_orphan_tickets_count,
    sum(case (is_containing_stuck_tickets) when true then 1 else 0 end) is_containing_stuck_tickets_count,
    sum(case (access_restricted = false and is_expired = false) when true then 1 else 0 end) as public_and_valid_count,
	event_id from ticket_category_statistics group by event_id) as stats
right outer join event on event_id = event.id order by event.start_ts, event.end_ts
);