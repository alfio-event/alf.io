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

-- we need to switch from the "postgres" role so we can apply the policy
create role application_user;


-- this function return true if the paramenter $1 (which is the organization id)
-- is present in the alfio.currentUserOrgs array OR simply
-- alfio.checkRowAccess is not set
create or replace function alfio_check_row_access(integer)
returns boolean
as
$$
    select
        coalesce(current_setting('alfio.checkRowAccess', true)::boolean, false) and
        $1 = ANY(coalesce(current_setting('alfio.currentUserOrgs', true), '{}')::integer[]) -- check if org_id is present in alfio.currentUserOrgs
$$ language sql;


-- enable row level security and create policy
alter table organization enable row level security;
create policy organization_access_policy on organization to application_user
    using (alfio_check_row_access(id))
    with check (alfio_check_row_access(id));


alter table j_user_organization enable row level security;
create policy j_user_organization_access_policy on j_user_organization to application_user
    using (alfio_check_row_access(org_id))
    with check (alfio_check_row_access(org_id));


alter table event enable row level security;
create policy event_access_policy on event to application_user
    using (alfio_check_row_access(org_id))
    with check (alfio_check_row_access(org_id));


alter table resource_organizer enable row level security;
create policy resource_organizer_access_policy on resource_organizer to application_user
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));


alter table resource_event enable row level security;
create policy resource_event_access_policy on resource_event to application_user
    using (alfio_check_row_access(organization_id_fk))
    with check (alfio_check_row_access(organization_id_fk));