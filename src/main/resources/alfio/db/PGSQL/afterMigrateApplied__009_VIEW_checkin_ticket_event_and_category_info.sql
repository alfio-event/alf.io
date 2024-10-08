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

-- This function checks (in a very basic and possibly "naive" way) if a given value is already encoded as a JSON array.
-- it is supposed to be a "temporary polyfill" until we set PostgreSQL 16 as minimum version
-- see https://www.postgresql.org/docs/current/functions-json.html#FUNCTIONS-SQLJSON-MISC
-- params:
--    value:      the value to be checked
--    field_type: additional field type
create or replace function retrieve_field_value_as_json(text, text)
    returns jsonb
as
$$
select
    case $2 when 'checkbox' then
            -- checkbox might be already encoded as JSON Array
            case $1 like '[%]' when true then $1::jsonb else jsonb_build_array($1) end
        else jsonb_build_array($1)
        end;
$$ language sql;

drop view if exists checkin_ticket_event_and_category_info;
create view checkin_ticket_event_and_category_info as
(
    select
        -- ticket

        t.id                                t_id,
        t.uuid                              t_uuid,
        t.public_uuid                       t_public_uuid,
        t.creation                          t_creation,
        t.category_id                       t_category_id,
        t.status                            t_status,
        t.event_id                          t_event_id,
        t.src_price_cts                     t_src_price_cts,
        t.final_price_cts                   t_final_price_cts,
        t.vat_cts                           t_vat_cts,
        t.discount_cts                      t_discount_cts,
        t.tickets_reservation_id            t_tickets_reservation_id,
        t.full_name                         t_full_name,
        t.first_name                        t_first_name,
        t.last_name                         t_last_name,
        t.email_address                     t_email_address,
        t.locked_assignment                 t_locked_assignment,
        t.user_language                     t_user_language,
        t.ext_reference                     t_ext_reference,
        t.currency_code                     t_currency_code,
        t.tags                              t_tags,
        t.subscription_id_fk                t_subscription_id,
        t.vat_status                        t_vat_status,

        -- ticket reservation

        tr.id                               tr_id,
        tr.validity                         tr_validity,
        tr.status                           tr_status,
        tr.full_name                        tr_full_name,
        tr.first_name                       tr_first_name,
        tr.last_name                        tr_last_name,
        tr.email_address                    tr_email_address,
        tr.billing_address                  tr_billing_address,
        tr.confirmation_ts                  tr_confirmation_ts,
        tr.latest_reminder_ts               tr_latest_reminder_ts,
        tr.payment_method                   tr_payment_method,
        tr.offline_payment_reminder_sent    tr_offline_payment_reminder_sent,
        tr.promo_code_id_fk                 tr_promo_code_id_fk,
        tr.automatic                        tr_automatic,
        tr.user_language                    tr_user_language,
        tr.direct_assignment                tr_direct_assignment,
        tr.invoice_number                   tr_invoice_number,
        tr.invoice_model                    tr_invoice_model,
        tr.vat_status                       tr_vat_status,
        tr.vat_nr                           tr_vat_nr,
        tr.vat_country                      tr_vat_country,
        tr.invoice_requested                tr_invoice_requested,
        tr.used_vat_percent                 tr_used_vat_percent,
        tr.vat_included                     tr_vat_included,
        tr.creation_ts                      tr_creation_ts,
        tr.customer_reference               tr_customer_reference,
        tr.registration_ts                  tr_registration_ts,

        tr.billing_address_company          tr_billing_address_company,
        tr.billing_address_line1            tr_billing_address_line1,
        tr.billing_address_line2            tr_billing_address_line2,
        tr.billing_address_city             tr_billing_address_city,
        tr.billing_address_state            tr_billing_address_state,
        tr.billing_address_zip              tr_billing_address_zip,
        tr.invoicing_additional_information tr_invoicing_additional_information,

        tr.src_price_cts                    tr_src_price_cts,
        tr.final_price_cts                  tr_final_price_cts,
        tr.vat_cts                          tr_vat_cts,
        tr.discount_cts                     tr_discount_cts,
        tr.currency_code                    tr_currency_code,

        -- ticket category
        tc.id                               tc_id,
        tc.inception                        tc_inception,
        tc.expiration                       tc_expiration,
        tc.max_tickets                      tc_max_tickets,
        tc.name                             tc_name,
        tc.src_price_cts                    tc_src_price_cts,
        tc.access_restricted                tc_access_restricted,
        tc.tc_status                        tc_tc_status,
        tc.event_id                         tc_event_id,
        tc.bounded                          tc_bounded,
        tc.category_code                    tc_category_code,
        tc.valid_checkin_from               tc_valid_checkin_from,
        tc.valid_checkin_to                 tc_valid_checkin_to,
        tc.ticket_validity_start            tc_ticket_validity_start,
        tc.ticket_validity_end              tc_ticket_validity_end,
        tc.ordinal                          tc_ordinal,
        tc.ticket_checkin_strategy          tc_ticket_checkin_strategy,
        tc.metadata                         tc_metadata,
        tc.ticket_access_type               tc_ticket_access_type,

        -- event

        e.id                                e_id,
        e.format                            e_format,
        e.short_name                        e_short_name,
        e.display_name                      e_display_name,
        e.start_ts                          e_start_ts,
        e.end_ts                            e_end_ts,
        e.time_zone                         e_time_zone,
        e.private_key                       e_private_key,
        e.metadata                          e_metadata,
        e.org_id                            e_org_id,
        e.locales                           e_locales,
        e.version                           e_version,
        (select jsonb_object_agg(tfc.field_name, retrieve_field_value_as_json(tfv.field_value, tfc.field_type)) as additional_info
            from purchase_context_field_value tfv
                inner join purchase_context_field_configuration tfc on tfv.field_configuration_id_fk = tfc.id
            where event_id_fk = e.id and tfv.ticket_id_fk = t.id)       tai_additional_info,
        case when t.status = 'ACQUIRED' or t.status = 'TO_BE_PAID' then 0 else 2 end as t_status_priority

    from ticket t
             inner join tickets_reservation tr on t.tickets_reservation_id = tr.id
             inner join ticket_category tc on t.category_id = tc.id
             inner join event e on e.id = t.event_id
    where t.status in ('ACQUIRED', 'CHECKED_IN', 'TO_BE_PAID')
      and t.first_name is not null
      and (t.first_name <> '') IS TRUE
      and t.last_name is not null
      and (t.last_name <> '') IS TRUE
      and t.email_address is not null
      and (t.email_address <> '') IS TRUE
);