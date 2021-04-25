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

alter table tickets_reservation add column user_id_fk integer constraint "reservation_user_id_fk" references ba_user(id);
-- create partial index for retrieving "my reservations"
create index idx_reservation_user_id on tickets_reservation(user_id_fk)
    where tickets_reservation.user_id_fk is not null;

create table user_profile (
    user_id_fk integer
        constraint "user_profile_user_id_fk" references ba_user(id)
        constraint "user_profile_user_id_unique" unique,
    billing_address_company text,
    billing_address_line1 text,
    billing_address_line2 text,
    billing_address_zip text,
    billing_address_city text,
    billing_address_state text,
    vat_country text,
    vat_nr text,
    invoicing_additional_information jsonb,
    additional_fields jsonb not null default '{}'
)
