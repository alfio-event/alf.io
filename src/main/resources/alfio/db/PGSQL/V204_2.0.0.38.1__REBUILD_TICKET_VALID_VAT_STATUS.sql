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

alter table ticket drop constraint ticket_valid_vat_status;

-- enforce VAT STATUS presence

alter table ticket add constraint ticket_valid_vat_status
    CHECK (
        status not in('PENDING', 'TO_BE_PAID', 'ACQUIRED', 'CHECKED_IN', 'PRE_RESERVED')
        or final_price_cts = 0
        or (final_price_cts <> 0 and status in('PENDING', 'TO_BE_PAID', 'ACQUIRED', 'CHECKED_IN', 'PRE_RESERVED') and vat_status is not null)
    );