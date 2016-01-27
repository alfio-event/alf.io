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

alter table email_message add column attempts integer DEFAULT 0 NOT null;

-- remove old job
delete from qrtz_simple_triggers where trigger_name = 'EnqueueNotSentEmail';
delete from qrtz_triggers where trigger_name = 'EnqueueNotSentEmail';
delete from qrtz_job_details where job_name = 'EnqueueNotSentEmail';