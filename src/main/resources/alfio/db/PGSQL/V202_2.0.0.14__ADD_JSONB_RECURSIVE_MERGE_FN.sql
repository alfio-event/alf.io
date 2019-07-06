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

-- from https://stackoverflow.com/a/42954907

-- note can return null when doing jsonb_recursive_merge('{}','{}');

create or replace function jsonb_recursive_merge(a jsonb, b jsonb)
returns jsonb language sql as $$
select
    jsonb_object_agg(
        coalesce(ka, kb),
        case
            when va isnull then vb
            when vb isnull then va
            when jsonb_typeof(va) <> 'object' or jsonb_typeof(vb) <> 'object' then vb
            else jsonb_recursive_merge(va, vb) end
        )
    from jsonb_each(a) e1(ka, va)
    full join jsonb_each(b) e2(kb, vb) on ka = kb
$$;