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

drop view if exists processing_fee_link_view;

create view processing_fee_link_view as (
    select pf.id id, pf.event_id_fk event_id_fk, pf.organization_id_fk organization_id_fk, pf.valid_from valid_from, pf.valid_to valid_to,
           pf.amount amount, pf.fee_type fee_type, pf.categories categories, pl.payment_method payment_method
    from processing_fee pf, processing_fee_link pl
        where pl.fee_id_fk = pf.id
);