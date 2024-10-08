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

drop view if exists additional_service_with_currency;
create view additional_service_with_currency as (
    select asv.id id,
        asv.event_id_fk  event_id_fk,
        asv.price_cts price_cts,
        asv.fix_price fix_price,
        asv.ordinal ordinal,
        asv.available_qty available_qty,
        asv.max_qty_per_order max_qty_per_order,
        asv.inception_ts inception_ts,
        asv.expiration_ts expiration_ts,
        asv.vat vat,
        asv.vat_type vat_type,

        asv.src_price_cts src_price_cts,
        asv.service_type service_type,
        asv.supplement_policy supplement_policy,
        asv.organization_id_fk organization_id_fk,
        e.currency currency_code,
        (case when asv.available_qty > 0 then (select count(*) from additional_service_item where additional_service_id_fk = asv.id and status = 'FREE') else 999 end) as available_count,
        asv.price_min_cts price_min_cts,
        asv.price_max_cts price_max_cts
    from additional_service asv, event e
    where asv.event_id_fk = e.id
)