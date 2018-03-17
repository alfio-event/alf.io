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

-- fix (again) mysql horrible auto updating behaviour, see http://stackoverflow.com/a/31865524
-- it's even worse than I thought: see https://stackoverflow.com/a/267675
ALTER TABLE additional_service_item CHANGE COLUMN  last_modified last_modified TIMESTAMP NOT NULL DEFAULT '2018-01-01 00:00:00';