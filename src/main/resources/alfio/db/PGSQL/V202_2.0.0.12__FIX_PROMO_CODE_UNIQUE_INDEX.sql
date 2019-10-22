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

alter table promo_code drop constraint if exists "unique_promo_code_for_org";
alter table promo_code drop constraint if exists "unique_promo_code_for_event";

create unique index if not exists "unique_promo_code_for_org" on promo_code(promo_code, organization_id_fk) where event_id_fk is null;
create unique index if not exists "unique_promo_code_for_event" on promo_code(promo_code, event_id_fk) where event_id_fk is not null;
