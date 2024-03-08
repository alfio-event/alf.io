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

drop view if exists promocode_usage_details;
create view promocode_usage_details as (
with tickets as (
    select t.tickets_reservation_id, json_agg(jsonb_build_object(
            'id', t.uuid,
            'firstName', t.first_name,
            'lastName', t.last_name,
            'type', tc.name,
            'status', t.status
    )) items
    from ticket t
    join ticket_category tc on t.category_id = tc.id
    group by t.tickets_reservation_id
),
reservations as (
    select pc.promo_code, tr.event_id_fk,
           jsonb_agg(jsonb_build_object(
                   'id', tr.id,
                   'invoiceNumber', tr.invoice_number,
                   'firstName', tr.first_name,
                   'lastName', tr.last_name,
                   'email', tr.email_address,
                   'paymentType', tr.payment_method,
                   'finalPriceCts', tr.final_price_cts,
                   'currency', tr.currency_code,
                   'confirmationTimestamp', to_char(tr.confirmation_ts at time zone 'UTC', 'YYYY-MM-DD') || 'T' || to_char(tr.confirmation_ts at time zone 'UTC', 'HH24:MI:SS.MSZ'),
                   'tickets', t.items
               )) as agg
    from tickets_reservation tr
    join promo_code pc ON tr.promo_code_id_fk = pc.id and
                          pc.organization_id_fk = tr.organization_id_fk and
                          (pc.event_id_fk is null or tr.event_id_fk = pc.event_id_fk)
    join tickets t on t.tickets_reservation_id = tr.id
    where tr.promo_code_id_fk is not null and tr.status in ('OFFLINE_PAYMENT', 'DEFERRED_OFFLINE_PAYMENT', 'COMPLETE', 'CANCELLED')
    group by pc.promo_code, tr.event_id_fk
)
select r.promo_code, e.id as event_id, e.short_name event_short_name, e.display_name event_display_name, r.agg reservations
from event e
    join reservations r on r.event_id_fk = e.id
    order by 1
);
