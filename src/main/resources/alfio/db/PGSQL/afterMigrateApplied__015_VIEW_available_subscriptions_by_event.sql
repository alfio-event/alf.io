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

drop view if exists available_subscriptions_by_event;
create view available_subscriptions_by_event as (
    with usage_by_subscription_id as (
        select s.id subscription_id,
               sum(case when t.subscription_id_fk is not null then 1 else 0 end) usage
        from subscription s
                 left join tickets_reservation t on t.subscription_id_fk = s.id
        group by 1
    )
    select e.id event_id,
           e.org_id organization_id,
           e.end_ts as e_end_ts,
           s.id as subscription_id,
           s.email_address as email_address,
           s.first_name as first_name,
           s.last_name as last_name,
           r.user_language as user_language,
           s.validity_from as s_validity_from,
           s.valid_to as s_validity_to
    from event e
             join subscription_event se on se.event_id_fk = e.id
             join subscription_descriptor sd on se.subscription_descriptor_id_fk = sd.id
             join subscription s on sd.id = s.subscription_descriptor_fk
             join usage_by_subscription_id u on s.id = u.subscription_id
             join tickets_reservation r on r.id = s.reservation_id_fk
    where s.status = 'ACQUIRED'
      and not exists(select id from tickets_reservation tr where tr.subscription_id_fk = s.id and tr.event_id_fk = e.id)
      and sd.supports_tickets_generation is TRUE
      and (s.max_usage = -1 or s.max_usage > u.usage)
    order by e.id
);
