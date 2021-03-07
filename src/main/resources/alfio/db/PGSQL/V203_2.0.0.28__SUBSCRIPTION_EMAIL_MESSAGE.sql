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

alter table email_message alter column event_id drop not null,
    add column subscription_descriptor_id_fk uuid constraint "email_message_subscription_descriptor_fk" references subscription_descriptor(id);

-- drop and recreate (partial) index on event_id
drop index idx_email_event_id;
create index idx_email_event_id on email_message(event_id) where event_id is not null;
-- create partial index on subscription_descriptor_id_fk
create index idx_email_subscription_descriptor on email_message(subscription_descriptor_id_fk) where subscription_descriptor_id_fk is not null;
-- create partial index on status
create index idx_email_subscription_status_process on email_message(status) where status = 'WAITING' or status = 'RETRY';
