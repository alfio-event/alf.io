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

-- add max entries per subscription
drop view if exists subscription_descriptor_statistics;
drop view if exists reservation_and_subscription_and_tx;
alter table subscription
    add column max_entries integer not null default -1,
    drop column usage_count;

with sd as (select id, max_entries from subscription_descriptor)
update subscription set max_entries = sd.max_entries from sd where status > 'FREE' and subscription_descriptor_fk = sd.id;

-- add subscription_id_fk at ticket level as well, so that we can understand which tickets have been acquired using a subscription
alter table ticket add column subscription_id_fk uuid constraint ticket_subscription_applied references subscription(id);
create index idx_ticket_subscription_id_fk on ticket(subscription_id_fk) where ticket.subscription_id_fk is not null;

-- migrate existing subscription applications to tickets
with cheaper_ticket_per_reservation as (
    select (select id from ticket where ticket.tickets_reservation_id = tr.id order by ticket.final_price_cts limit 1) as id, tr.subscription_id_fk
        from ticket t
        join tickets_reservation tr on t.tickets_reservation_id = tr.id
    where tr.subscription_id_fk is not null
)
update ticket set subscription_id_fk = cheaper_ticket_per_reservation.subscription_id_fk from cheaper_ticket_per_reservation where ticket.id = cheaper_ticket_per_reservation.id;

-- add validation when applying subscription
CREATE OR REPLACE FUNCTION trf_check_subscription_usage()
    RETURNS TRIGGER AS
$body$
DECLARE
    r_count numeric;
    max_allowed numeric;
    single_entry_for_event boolean;
    definition record;
BEGIN
    IF (NEW.subscription_id_fk is not null) THEN
        select s.max_entries, sd.usage_type = 'ONCE_PER_EVENT' as single_entry from subscription s
            join subscription_descriptor sd on s.subscription_descriptor_fk = sd.id where s.id = NEW.subscription_id_fk
        into definition;
        max_allowed = definition.max_entries;
        single_entry_for_event = definition.single_entry;
        IF (max_allowed > 0 or definition.single_entry) THEN
            -- serialize by locking all reservations for the same subscription
            PERFORM * from ticket where subscription_id_fk = NEW.subscription_id_fk for update;
            IF (max_allowed > 0) THEN
                -- subscription has a max_entries limit. Check if the max number has been reached
                r_count = (select count(*) from ticket where subscription_id_fk = NEW.subscription_id_fk);
                IF (r_count > max_allowed) THEN
                    raise 'MAX_ENTRIES_OVERAGE' USING DETAIL = ('{ "allowed": ' || max_allowed || ', "requested": '|| r_count || '}');
                END IF;
            END IF;
            IF single_entry_for_event THEN
                -- subscription can be used only once per event
                r_count = (select count(*) from ticket
                    where subscription_id_fk = NEW.subscription_id_fk
                    and event_id = NEW.event_id);
                IF (r_count > 1) THEN
                    raise 'ONCE_PER_EVENT_OVERAGE' USING DETAIL = ('{ "allowed": 1, "requested": '|| r_count || '}');
                END IF;
            END IF;
        END IF;
    END IF;
    RETURN NULL;
END
$body$
    LANGUAGE plpgsql;

CREATE TRIGGER tr_check_subscription_usage
    AFTER UPDATE OF subscription_id_fk ON ticket
    FOR EACH ROW EXECUTE PROCEDURE trf_check_subscription_usage();

