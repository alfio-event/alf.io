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