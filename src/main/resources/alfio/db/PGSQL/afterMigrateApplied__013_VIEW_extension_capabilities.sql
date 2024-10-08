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

drop view if exists extension_capabilities;
create view extension_capabilities as (
    select es.es_id,
           es.path,
           es.name,
           es.hash,
           es.enabled,
           es.async,
           es.script,
           es.display_name,
           cap.value as capability,
           ev.events,
           (select jsonb_agg(value) from jsonb_array_elements(es.metadata -> 'capabilityDetails') as capd(value)
              where jsonb_typeof(es.metadata -> 'capabilityDetails') = 'array'
              and (capd.value is null or capd.value @> ('{"key": "' || cap.value || '"}')::jsonb)) as capability_detail
    from extension_support es
    cross join lateral (
        select * from jsonb_array_elements_text(es.metadata -> 'capabilities') as cap(value)
            where jsonb_typeof(es.metadata -> 'capabilities') = 'array'
    ) cap,
    lateral (
        select jsonb_agg(value) as events from jsonb_array_elements_text(es.metadata->'events')
            where jsonb_typeof(es.metadata -> 'events') = 'array'
    ) ev
    where enabled = true
      and metadata is not null
);