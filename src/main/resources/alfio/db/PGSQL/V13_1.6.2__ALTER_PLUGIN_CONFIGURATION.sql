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

--set the default to -1 in order to mark the existing plugin configuration and migrate it later
alter table plugin_configuration add COLUMN event_id integer DEFAULT -1 NOT null;
alter table PLUGIN_CONFIGURATION drop constraint "unique_plugin_conf";
alter table PLUGIN_CONFIGURATION add constraint "unique_plugin_conf" unique(plugin_id, event_id, conf_name);