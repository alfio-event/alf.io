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
    )
    select res.*,
           'event' as pc_type,
           e.time_zone as pc_time_zone,
           e.short_name as pc_public_identifier,
           jsonb_build_object('en', e.display_name) as pc_title
    from reservations res join event e on e.id = res.tr_event_id_fk
    union
    select res.*,
           'subscription' as pc_type,
           sd.time_zone as pc_time_zone,
           sd.id::text as pc_public_identifier,
           sd.title as pc_title from reservations res
        join subscription s on s.reservation_id_fk = res.tr_id
        join subscription_descriptor sd on sd.id = s.subscription_descriptor_fk

);