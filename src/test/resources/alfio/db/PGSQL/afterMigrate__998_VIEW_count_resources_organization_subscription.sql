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

create or replace view count_resources_assigned_to_subscription_org as (

    select count(*) cnt, organization_id_fk org, subscription_descriptor_fk sid, 'subscription' entity from subscription group by 3, 2

    union all
    select count(*) cnt, a.organization_id_fk org, s.subscription_descriptor_fk sid, 'auditing' entity from auditing a
        join subscription s on a.reservation_id = s.reservation_id_fk
        group by 3, 2

    union all
    select count(*) cnt, bd.organization_id_fk org, s.subscription_descriptor_fk sid, 'billing_document' entity from billing_document bd
        join subscription s on bd.reservation_id_fk = s.reservation_id_fk
        group by 3, 2

    union all
    select count(*) cnt, em.organization_id_fk org, s.subscription_descriptor_fk sid, 'email_message' entity from email_message em
        join subscription s on em.reservation_id = s.reservation_id_fk
        group by 3, 2

    union all
    select count(*) cnt, pc.organization_id_fk org, s.subscription_descriptor_fk sid, 'promo_code' entity from promo_code pc
        join tickets_reservation r on r.promo_code_id_fk = pc.id
        join subscription s on s.reservation_id_fk = r.id
        group by 3, 2

    union all
    select count(*) cnt, tra.organization_id_fk org, s.subscription_descriptor_fk sid, 'b_transaction' entity from b_transaction tra
        join tickets_reservation tr on tra.reservation_id = tr.id
        join subscription s on s.reservation_id_fk = tr.id
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, subscription_descriptor_id_fk sid, 'subscription_event' entity from subscription_event group by 3, 2

    union all
    select count(*) cnt, tr.organization_id_fk org, s.subscription_descriptor_fk sid, 'tickets_reservation' entity from tickets_reservation tr
        join subscription s on s.reservation_id_fk = tr.id
        group by 3, 2

);
