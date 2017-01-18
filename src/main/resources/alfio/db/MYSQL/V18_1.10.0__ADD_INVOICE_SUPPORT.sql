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

-- fix mysql horrible auto updating behaviour, see http://stackoverflow.com/a/31865524


ALTER TABLE ticket CHANGE COLUMN  creation creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ticket ALTER COLUMN creation DROP DEFAULT;

ALTER TABLE tickets_reservation CHANGE COLUMN  validity validity TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE tickets_reservation ALTER COLUMN validity DROP DEFAULT;

ALTER TABLE b_transaction CHANGE COLUMN  t_timestamp t_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE b_transaction ALTER COLUMN t_timestamp DROP DEFAULT;

ALTER TABLE plugin_log CHANGE COLUMN  event_ts event_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE plugin_log ALTER COLUMN event_ts DROP DEFAULT;

ALTER TABLE waiting_queue CHANGE COLUMN  creation creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE waiting_queue ALTER COLUMN creation DROP DEFAULT;

ALTER TABLE sponsor_scan CHANGE COLUMN  creation creation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sponsor_scan ALTER COLUMN creation DROP DEFAULT;
--

create table invoice_sequences (
    event_id_fk integer primary key not null,
    sequence integer not null
);

alter table invoice_sequences add foreign key(event_id_fk) references event(id);

insert into invoice_sequences select id, 0 from event;

alter table tickets_reservation add column invoice_number varchar(256);
alter table tickets_reservation add column invoice_model MEDIUMTEXT;