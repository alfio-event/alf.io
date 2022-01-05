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

-- this function propagates organization id change to event's descendant resources
create or replace function propagate_subscription_organization_change() returns trigger
as $$
DECLARE
    subscription_links_count numeric;
BEGIN
    if new.organization_id_fk <> old.organization_id_fk then
        -- organizationId has changed.
        -- We need to migrate all descendant resources to the new organization

        -- first we check if there are any incompatible links in place
        subscription_links_count := (select count(*) from subscription_event where subscription_descriptor_id_fk = old.id);
        if subscription_links_count > 0 then
            raise 'CANNOT_TRANSFER_SUBSCRIPTION_LINK' USING DETAIL = ('{count:' || subscription_links_count || '}');
        end if;

        -- subscription
        update subscription set organization_id_fk = new.organization_id_fk where subscription_descriptor_fk = old.id;

        -- ticket reservations
        update tickets_reservation set organization_id_fk = new.organization_id_fk
            from subscription s
            where s.reservation_id_fk = tickets_reservation.id and s.subscription_descriptor_fk = old.id;

        -- auditing
        update auditing set organization_id_fk = new.organization_id_fk
            from subscription s
            where s.reservation_id_fk = auditing.reservation_id and s.subscription_descriptor_fk = old.id;

        -- billing_document
        update billing_document set organization_id_fk = new.organization_id_fk
            from subscription s
            where s.reservation_id_fk = billing_document.reservation_id_fk and s.subscription_descriptor_fk = old.id;

        -- messages
        update email_message set organization_id_fk = new.organization_id_fk
            from subscription s
            where s.reservation_id_fk = email_message.reservation_id and s.subscription_descriptor_fk = old.id;

        -- promo code
        update promo_code set organization_id_fk = new.organization_id_fk
            from tickets_reservation tr
            join subscription s on tr.id = s.reservation_id_fk
            where tr.promo_code_id_fk = promo_code.id and s.subscription_descriptor_fk = old.id;

        -- transaction
        update b_transaction set organization_id_fk = new.organization_id_fk
            from tickets_reservation tr
            join subscription s on tr.id = s.reservation_id_fk
            where tr.id = b_transaction.reservation_id and s.subscription_descriptor_fk = old.id;

    end if;
    return new;

END
$$ language plpgsql;

create trigger subscription_update_org_id_fk_trigger
    after update on subscription_descriptor
    for each row execute procedure propagate_subscription_organization_change();

