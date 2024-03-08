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

drop view if exists reservation_with_purchase_context;
create view reservation_with_purchase_context as (

    with reservations as (
        select
            id tr_id,
            validity tr_validity,
            status tr_status,
            creation_ts tr_creation_ts,
            confirmation_ts tr_confirmation_ts,
            registration_ts tr_registration_ts,
            payment_method tr_payment_method,
            invoice_number tr_invoice_number,
            vat_status tr_vat_status,
            used_vat_percent tr_used_vat_percent,
            src_price_cts tr_src_price_cts,
            final_price_cts tr_final_price_cts,
            vat_cts tr_vat_cts,
            discount_cts tr_discount_cts,
            currency_code tr_currency_code,
            event_id_fk tr_event_id_fk,
            user_id_fk tr_user_id_fk
        from tickets_reservation where user_id_fk is not null and status <> 'CANCELLED'
    ),
    ticketsJsonb as (
        select
            jsonb_agg(jsonb_build_object(
                'id', t.uuid,
                'firstName', t.first_name,
                'lastName', t.last_name,
                'type', jsonb_build_object('en', tc.name))) t_tickets,
            t.tickets_reservation_id t_reservation_id
        from ticket t left join ticket_category tc on t.category_id = tc.id
        group by t.tickets_reservation_id
    ),
    subscriptionsJsonb as (
        select
            jsonb_agg(jsonb_build_object(
                'id', s.id,
                'firstName', s.first_name,
                'lastName', s.last_name,
                'type', d.title)) s_subscriptions,
            s.reservation_id_fk s_reservation_id
        from subscription s
            join subscription_descriptor d on s.subscription_descriptor_fk = d.id
            group by s.reservation_id_fk
    )
    select res.*,
           'event' as pc_type,
           e.time_zone as pc_time_zone,
           e.start_ts as pc_start_date,
           e.end_ts as pc_end_date,
           e.short_name as pc_public_identifier,
           jsonb_build_object('en', e.display_name) as pc_title,
           t.t_tickets pc_items
    from reservations res
        join event e on e.id = res.tr_event_id_fk
        join ticketsJsonb t on t.t_reservation_id = res.tr_id
    union
    select res.*,
           'subscription' as pc_type,
           sd.time_zone as pc_time_zone,
           s.validity_from as pc_start_date,
           s.validity_to as pc_end_date,
           sd.id::text as pc_public_identifier,
           sd.title as pc_title,
           sj.s_subscriptions pc_items
        from reservations res
        join subscription s on s.reservation_id_fk = res.tr_id
        join subscription_descriptor sd on sd.id = s.subscription_descriptor_fk
        join subscriptionsJsonb sj on sj.s_reservation_id = res.tr_id
);