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

create view extension_capabilities as (
    select es.es_id,
           es.path,
           es.name,
           es.hash,
           es.enabled,
           es.async,
           es.script,
           es.display_name,
           cap.value capability,
           events
    from extension_support es
    cross join lateral (
        select * from jsonb_array_elements_text(es.metadata->'capabilities') as cap
    ) cap
    cross join lateral (
        select array_agg(value::text) as events from jsonb_array_elements_text(es.metadata->'events')
    ) ev
    where enabled = true
);