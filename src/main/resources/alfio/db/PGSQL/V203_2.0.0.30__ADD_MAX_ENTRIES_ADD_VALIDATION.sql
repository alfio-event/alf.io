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

alter table subscription
    add column max_entries integer not null default -1,
    drop column usage_count;

with sd as (select id, max_entries from subscription_descriptor)
update subscription set max_entries = sd.max_entries from sd where status > 'FREE' and subscription_descriptor_fk = sd.id;

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
            PERFORM * from tickets_reservation where subscription_id_fk = NEW.subscription_id_fk for update;
            IF (max_allowed > 0) THEN
                -- subscription has a max_entries limit. Check if the max number has been reached
                r_count = (select count(*) from tickets_reservation where subscription_id_fk = NEW.subscription_id_fk);
                IF (r_count > max_allowed) THEN
                    raise 'MAX_ENTRIES_OVERAGE' USING DETAIL = ('{ "allowed": ' || max_allowed || ', "requested": '|| r_count || '}');
                END IF;
            END IF;
            IF single_entry_for_event THEN
                -- subscription can be used only once per event
                r_count = (select count(*) from tickets_reservation
                    where subscription_id_fk = NEW.subscription_id_fk
                    and event_id_fk = NEW.event_id_fk and status <> 'CANCELLED');
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
    AFTER UPDATE OF subscription_id_fk ON tickets_reservation
    FOR EACH ROW EXECUTE PROCEDURE trf_check_subscription_usage();

