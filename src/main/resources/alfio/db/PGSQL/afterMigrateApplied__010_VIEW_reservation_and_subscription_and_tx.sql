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

drop view if exists subscription_descriptor_statistics;
drop view if exists reservation_and_subscription_and_tx;
create view reservation_and_subscription_and_tx as (select

    tickets_reservation.id tr_id,
    tickets_reservation.validity tr_validity,
    tickets_reservation.status tr_status,
    tickets_reservation.full_name tr_full_name,
    tickets_reservation.first_name tr_first_name,
    tickets_reservation.last_name tr_last_name,
    tickets_reservation.email_address tr_email_address,
    tickets_reservation.billing_address tr_billing_address,
    tickets_reservation.confirmation_ts tr_confirmation_ts,
    tickets_reservation.latest_reminder_ts tr_latest_reminder_ts,
    tickets_reservation.payment_method tr_payment_method,
    tickets_reservation.offline_payment_reminder_sent tr_offline_payment_reminder_sent,
    tickets_reservation.promo_code_id_fk tr_promo_code_id_fk,
    tickets_reservation.automatic tr_automatic,
    tickets_reservation.user_language tr_user_language,
    tickets_reservation.direct_assignment tr_direct_assignment,
    tickets_reservation.invoice_number tr_invoice_number,
    tickets_reservation.invoice_model tr_invoice_model,
    tickets_reservation.vat_status tr_vat_status,
    tickets_reservation.vat_nr tr_vat_nr,
    tickets_reservation.vat_country tr_vat_country,
    tickets_reservation.invoice_requested tr_invoice_requested,
    tickets_reservation.used_vat_percent tr_used_vat_percent,
    tickets_reservation.vat_included tr_vat_included,
    tickets_reservation.creation_ts tr_creation_ts,
    tickets_reservation.customer_reference tr_customer_reference,
    tickets_reservation.billing_address_company tr_billing_address_company,
    tickets_reservation.billing_address_line1 tr_billing_address_line1,
    tickets_reservation.billing_address_line2 tr_billing_address_line2,
    tickets_reservation.billing_address_city tr_billing_address_city,
    tickets_reservation.billing_address_zip tr_billing_address_zip,
    tickets_reservation.registration_ts tr_registration_ts,
    tickets_reservation.invoicing_additional_information tr_invoicing_additional_information,

    tickets_reservation.src_price_cts tr_src_price_cts,
    tickets_reservation.final_price_cts tr_final_price_cts,
    tickets_reservation.vat_cts tr_vat_cts,
    tickets_reservation.discount_cts tr_discount_cts,
    tickets_reservation.currency_code tr_currency_code,

    subscription.id s_id,
    subscription.first_name s_first_name,
    subscription.last_name s_last_name,
    subscription.email_address s_email_address,
    (select count(*) from tickets_reservation tr where tr.subscription_id_fk = subscription.id) s_usage_count,
    subscription.max_usage s_max_usage,
    subscription.valid_from s_valid_from,
    subscription.valid_to s_valid_to,
    subscription.src_price_cts s_src_price_cts,
    subscription.final_price_cts s_final_price_cts,
    subscription.vat_cts s_vat_cts,
    subscription.discount_cts s_discount_cts,
    subscription.currency s_currency,
    subscription.organization_id_fk s_organization_id,
    subscription.creation_ts s_creation_ts,
    subscription.update_ts s_update_ts,
    subscription.status s_status,
    subscription.subscription_descriptor_fk s_descriptor_id,

    b_transaction.id bt_id,
    b_transaction.gtw_tx_id bt_gtw_tx_id,
    b_transaction.gtw_payment_id bt_gtw_payment_id,
    b_transaction.reservation_id bt_reservation_id,
    b_transaction.t_timestamp bt_t_timestamp,
    b_transaction.price_cts bt_price_cts,
    b_transaction.currency bt_currency,
    b_transaction.description bt_description,
    b_transaction.payment_proxy bt_payment_proxy,
    b_transaction.gtw_fee bt_gtw_fee,
    b_transaction.plat_fee bt_plat_fee,
    b_transaction.status bt_status,
    b_transaction.metadata bt_metadata,

    promo_code.promo_code as promo_code

from tickets_reservation
join subscription on subscription.reservation_id_fk = tickets_reservation.id
left outer join b_transaction on tickets_reservation.id = b_transaction.reservation_id and b_transaction.status <> 'INVALID'
left outer join promo_code on tickets_reservation.promo_code_id_fk = promo_code.id
);

create view subscription_descriptor_statistics as (
      select
            sd.id sd_id,
            sd.title sd_title,
            sd.description sd_description,
            sd.max_available sd_max_available,
            sd.creation_ts sd_creation_ts,
            sd.on_sale_from sd_on_sale_from,
            sd.on_sale_to sd_on_sale_to,
            sd.price_cts sd_price_cts,
            sd.vat sd_vat,
            sd.vat_status sd_vat_status,
            sd.currency sd_currency,
            sd.is_public sd_is_public,
            sd.organization_id_fk sd_organization_id_fk,
            sd.max_entries sd_max_entries,
            sd.validity_type sd_validity_type,
            sd.validity_time_unit sd_validity_time_unit,
            sd.validity_units sd_validity_units,
            sd.validity_from sd_validity_from,
            sd.validity_to sd_validity_to,
            sd.usage_type sd_usage_type,
            sd.terms_conditions_url sd_terms_conditions_url,
            sd.privacy_policy_url sd_privacy_policy_url,
            sd.file_blob_id_fk sd_file_blob_id_fk,
            sd.allowed_payment_proxies sd_allowed_payment_proxies,
            sd.private_key sd_private_key,
            sd.time_zone sd_time_zone,
            sd.supports_tickets_generation sd_supports_tickets_generation,
            (select count(*) from reservation_and_subscription_and_tx where s_descriptor_id = sd.id) s_reservations_count,
            (select count(*) from subscription where status between 'ACQUIRED' and 'CHECKED_IN' and subscription_descriptor_fk = sd.id) s_sold_count,
            (select count(*) from subscription where status = 'PENDING' and subscription_descriptor_fk = sd.id) s_pending_count,
            (select count(*) from subscription_event where subscription_descriptor_id_fk = sd.id) s_events_count
      from subscription_descriptor sd
        where sd.status = 'ACTIVE'
        order by on_sale_from, on_sale_to nulls last
)