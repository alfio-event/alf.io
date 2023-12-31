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

create or replace view count_resources_assigned_to_event_org as (
    select count(*) cnt, organization_id_fk org, event_id ev, 'ticket' entity from ticket group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'ticket_category' entity from ticket_category group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'additional_service' entity from additional_service group by 3, 2

    union all
    select count(*) cnt, asd.organization_id_fk org, ase.event_id_fk ev, 'additional_service_description' entity from additional_service_description asd
        join additional_service ase on ase.id = asd.additional_service_id_fk
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'additional_service_item' entity from additional_service_item group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'admin_reservation_request' entity from admin_reservation_request group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'auditing' entity from auditing group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'billing_document' entity from billing_document group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'configuration_event' entity from configuration_event group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'configuration_purchase_context' entity from configuration_purchase_context where event_id_fk is not null group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'configuration_ticket_category' entity from configuration_ticket_category group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'email_message' entity from email_message group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'event_description_text' entity from event_description_text group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'group_link' entity from group_link group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'poll' entity from poll group by 3, 2

    union all
    select count(*) cnt, pa.organization_id_fk org, p.event_id_fk ev, 'poll_answer' entity from poll_answer pa
        join poll p on pa.poll_id_fk = p.id
        group by 3, 2

    union all
    select count(*) cnt, po.organization_id_fk org, p.event_id_fk ev, 'poll_option' entity from poll_option po
        join poll p on po.poll_id_fk = p.id
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'promo_code' entity from promo_code group by 3, 2

    union all
    select count(*) cnt, tra.organization_id_fk org, tr.event_id_fk ev, 'b_transaction' entity from b_transaction tra
        join tickets_reservation tr on tra.reservation_id = tr.id
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'resource_event' entity from resource_event group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'scan_audit' entity from scan_audit group by 3, 2

    union all
    select count(*) cnt, sp.organization_id_fk org, tc.event_id ev, 'special_price' entity from special_price sp
        join ticket_category tc on sp.ticket_category_id = tc.id
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'sponsor_scan' entity from sponsor_scan group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'subscription_event' entity from subscription_event group by 3, 2


    union all
    select count(*) cnt, tt.organization_id_fk org, tc.event_id ev, 'ticket_category_text' entity from ticket_category_text tt
        join ticket_category tc on tt.ticket_category_id_fk = tc.id
        group by 3, 2

    -- checks if there is any leftovers from the migration
    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'ticket_field_configuration' entity from ticket_field_configuration group by 3, 2

    union all
    select count(*) cnt, tfd.organization_id_fk org, event_id_fk ev, 'ticket_field_description' entity from ticket_field_description tfd
        join ticket_field_configuration tfc on tfd.ticket_field_configuration_id_fk = tfc.id
        group by 3, 2

    union all
    select count(*) cnt, tfv.organization_id_fk org, event_id_fk ev, 'ticket_field_value' entity from ticket_field_value tfv
        join ticket_field_configuration tfc on tfv.ticket_field_configuration_id_fk = tfc.id
        group by 3, 2

    -- purchaseContext fields

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'purchase_context_field_configuration' entity from purchase_context_field_configuration group by 3, 2

    union all
    select count(*) cnt, tfd.organization_id_fk org, event_id_fk ev, 'purchase_context_field_description' entity from purchase_context_field_description tfd
        join purchase_context_field_configuration tfc on tfd.field_configuration_id_fk = tfc.id
        group by 3, 2

    union all
    select count(*) cnt, tfv.organization_id_fk org, event_id_fk ev, 'purchase_context_field_value' entity from purchase_context_field_value tfv
        join purchase_context_field_configuration tfc on tfv.field_configuration_id_fk = tfc.id
        group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id_fk ev, 'tickets_reservation' entity from tickets_reservation group by 3, 2

    union all
    select count(*) cnt, organization_id_fk org, event_id ev, 'waiting_queue' entity from waiting_queue group by 3, 2

    union all
    select count(*) cnt, wt.organization_id_fk org, gl.event_id_fk ev, 'whitelisted_ticket' entity from whitelisted_ticket wt
        join group_link gl on gl.id = wt.group_link_id_fk
        group by 3, 2
);
