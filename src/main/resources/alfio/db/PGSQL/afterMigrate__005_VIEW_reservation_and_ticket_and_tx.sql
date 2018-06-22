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

drop view if exists reservation_and_ticket_and_tx;
create view reservation_and_ticket_and_tx as (select

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
    tickets_reservation.event_id_fk tr_event_id,
    tickets_reservation.creation_ts tr_creation_ts,
    tickets_reservation.customer_reference tr_customer_reference,

    ticket.id t_id,
    ticket.uuid t_uuid,
    ticket.creation t_creation,
    ticket.category_id t_category_id,
    ticket.status t_status,
    ticket.event_id t_event_id,
    ticket.tickets_reservation_id t_tickets_reservation_id,
    ticket.full_name t_full_name,
    ticket.first_name t_first_name,
    ticket.last_name t_last_name,
    ticket.email_address t_email_address,
    ticket.locked_assignment t_locked_assignment,
    ticket.user_language t_user_language,
    ticket.src_price_cts t_src_price_cts,
    ticket.final_price_cts t_final_price_cts,
    ticket.vat_cts t_vat_cts,
    ticket.discount_cts t_discount_cts,
    ticket.ext_reference t_ext_reference,

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
    b_transaction.plat_fee bt_plat_fee

from tickets_reservation
left outer join ticket on tickets_reservation.id = ticket.tickets_reservation_id
left outer join b_transaction on ticket.tickets_reservation_id = b_transaction.reservation_id);