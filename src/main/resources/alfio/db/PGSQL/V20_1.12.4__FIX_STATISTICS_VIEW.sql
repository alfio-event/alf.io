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

drop view events_statistics;

create view events_statistics as (select
      event.id,
      event.available_seats,
      case(contains_unbounded_categories) when true then 0 else event.available_seats - allocated_count end as not_allocated_tickets,
      pending_count as pending_tickets,
      sold_tickets_count as sold_tickets,
      stats.checked_in_count as checked_in_tickets,
      case(contains_unbounded_categories) when true then
        event.available_seats
          - allocated_count
          - sold_tickets_count_unbounded
          - checked_in_count_unbounded
          - pending_count_unbounded
          - (select count(*) from ticket where status = 'RELEASED' and category_id is null and event_id = event.id)
          else 0 end as dynamic_allocation,
      case (contains_unbounded_categories) when true then
        allocated_count - sold_tickets_count_bounded - checked_in_count_bounded - pending_count
      else
        allocated_count - sold_tickets_count - stats.checked_in_count - pending_count
      end as not_sold_tickets,
      is_containing_orphan_tickets_count > 0 as is_containing_orphan_tickets,
      is_containing_stuck_tickets_count > 0 as is_containing_stuck_tickets_count


from
(select
	sum(sold_tickets_count) as sold_tickets_count,
	sum(checked_in_count) as checked_in_count,
	sum(pending_count) as pending_count,
	sum(case (bounded) when true then checked_in_count else 0 end) as checked_in_count_bounded,
	sum(case (bounded = false) when true then checked_in_count else 0 end) as checked_in_count_unbounded,
	sum(case (bounded) when true then max_tickets else 0 end) as allocated_count,
	sum(case (bounded) when true then sold_tickets_count else 0 end) as sold_tickets_count_bounded,
	sum(case (bounded = false) when true then sold_tickets_count else 0 end) as sold_tickets_count_unbounded,
	sum(case (bounded = false) when true then pending_count else 0 end) as pending_count_unbounded,
	sum(case (bounded) when false then 1 else 0 end) > 0 contains_unbounded_categories,
	sum(case (is_containing_orphan_tickets) when true then 1 else 0 end) is_containing_orphan_tickets_count,
    sum(case (is_containing_stuck_tickets) when true then 1 else 0 end) is_containing_stuck_tickets_count,
	event_id from ticket_category_statistics group by event_id) as stats
inner join event on event_id = event.id);