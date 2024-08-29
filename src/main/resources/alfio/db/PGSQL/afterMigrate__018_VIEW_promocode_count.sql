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

drop view if exists promocode_count;

create view promocode_count as (
select id as promo_code_id, promo_code, coalesce(promo_code_use,0) as promo_code_use
from (
    select id, promo_code from promo_code pc
) as pc_data
left join (
    select pc.id promo_code_id, count(*) as promo_code_use from tickets_reservation tr
    inner join promo_code pc on tr.promo_code_id_fk = pc.id
    left join ticket on ticket.tickets_reservation_id = tr.id
    where tr.status in ('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'STUCK')
    and (
        -- is not assigned to category?
        pc.categories is null or json_array_length(pc.categories::json) = 0 or
        -- v category_id is inside pc.categories?
        ((pc.categories::jsonb) @> ((ticket.category_id::text)::jsonb))
    ) group by pc.id
) as count_data on pc_data.id = count_data.promo_code_id
);



drop view if exists promocode_count_all;

create view promocode_count_all as (
select id as promo_code_id, promo_code, coalesce(promo_code_use,0) as promo_code_use
from (
    select id, promo_code from promo_code pc
) as pc_data
left join (
    select pc.id promo_code_id, count(*) as promo_code_use
    from tickets_reservation tr
    inner join promo_code pc on tr.promo_code_id_fk = pc.id
    left join ticket on ticket.tickets_reservation_id = tr.id
    where tr.status <> 'CANCELLED'
    and (
        -- is not assigned to category?
        pc.categories is null or json_array_length(pc.categories::json) = 0 or
        -- v category_id is inside pc.categories?
        ((pc.categories::jsonb) @> ((ticket.category_id::text)::jsonb))
    )
    group by pc.id
) as count_data on pc_data.id = count_data.promo_code_id
);


